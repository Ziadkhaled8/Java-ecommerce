package com.ecommerce.dreamstore.payment.circuitbreaker;

import com.ecommerce.dreamstore.exception.CircuitBreakerOpenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Thread-safe lightweight Circuit Breaker implementation for payment gateways.
 * <p>
 * States:
 * - CLOSED: Calls go through. Consecutive failures increment a counter. When threshold is met, transitions to OPEN.
 * - OPEN: Calls fast-fail immediately with {@link CircuitBreakerOpenException} until reset timeout expires.
 * - HALF_OPEN: Trial calls are permitted. If trial succeeds (meeting threshold), recovers to CLOSED; if fails, reverts to OPEN.
 */
@Component
@Slf4j
public class PaymentCircuitBreaker {

    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final int halfOpenSuccessThreshold;

    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicLong lastStateChangedTime = new AtomicLong(System.currentTimeMillis());

    public PaymentCircuitBreaker(
            @Value("${payment.circuit-breaker.name:payment-gateway-cb}") String name,
            @Value("${payment.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${payment.circuit-breaker.reset-timeout-ms:30000}") long resetTimeoutMs,
            @Value("${payment.circuit-breaker.half-open-success-threshold:2}") int halfOpenSuccessThreshold
    ) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
        log.info("Initialized CircuitBreaker '{}' [threshold={}, resetTimeoutMs={}, halfOpenSuccessThreshold={}]",
                name, failureThreshold, resetTimeoutMs, halfOpenSuccessThreshold);
    }

    public <T> T execute(Supplier<T> supplier) {
        checkAndAttemptStateTransition();

        CircuitBreakerState currentState = state.get();
        if (currentState == CircuitBreakerState.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChangedTime.get();
            long retryAfterSeconds = Math.max(1, (resetTimeoutMs - elapsed) / 1000);
            log.warn("Circuit breaker '{}' is OPEN. Fast-failing execution. Retry after {}s", name, retryAfterSeconds);
            throw new CircuitBreakerOpenException(name, retryAfterSeconds);
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception ex) {
            onFailure(ex);
            throw ex;
        }
    }

    public void executeRunnable(Runnable runnable) {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    private void checkAndAttemptStateTransition() {
        if (state.get() == CircuitBreakerState.OPEN) {
            long now = System.currentTimeMillis();
            if (now - lastStateChangedTime.get() >= resetTimeoutMs) {
                if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                    lastStateChangedTime.set(now);
                    halfOpenSuccessCount.set(0);
                    log.info("Circuit breaker '{}' reset timeout expired. Transitioning OPEN -> HALF_OPEN (probing gateway)", name);
                }
            }
        }
    }

    private void onSuccess() {
        CircuitBreakerState currentState = state.get();
        if (currentState == CircuitBreakerState.HALF_OPEN) {
            int successes = halfOpenSuccessCount.incrementAndGet();
            log.debug("Circuit breaker '{}' HALF_OPEN trial success ({}/{})", name, successes, halfOpenSuccessThreshold);
            if (successes >= halfOpenSuccessThreshold) {
                if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                    lastStateChangedTime.set(System.currentTimeMillis());
                    failureCount.set(0);
                    halfOpenSuccessCount.set(0);
                    log.info("Circuit breaker '{}' recovered successfully! Transitioning HALF_OPEN -> CLOSED", name);
                }
            }
        } else if (currentState == CircuitBreakerState.CLOSED) {
            if (failureCount.get() > 0) {
                failureCount.set(0);
            }
        }
    }

    private void onFailure(Throwable ex) {
        CircuitBreakerState currentState = state.get();
        long now = System.currentTimeMillis();

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            if (state.compareAndSet(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                lastStateChangedTime.set(now);
                log.warn("Circuit breaker '{}' trial failed in HALF_OPEN ({}). Transitioning back to OPEN", name, ex.getMessage());
            }
        } else if (currentState == CircuitBreakerState.CLOSED) {
            int currentFailures = failureCount.incrementAndGet();
            log.warn("Circuit breaker '{}' recorded failure ({}/{}): {}", name, currentFailures, failureThreshold, ex.getMessage());
            if (currentFailures >= failureThreshold) {
                if (state.compareAndSet(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                    lastStateChangedTime.set(now);
                    log.error("Circuit breaker '{}' reached failure threshold ({}). Transitioning CLOSED -> OPEN", name, failureThreshold);
                }
            }
        }
    }

    public CircuitBreakerState getState() {
        checkAndAttemptStateTransition();
        return state.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public void reset() {
        state.set(CircuitBreakerState.CLOSED);
        failureCount.set(0);
        halfOpenSuccessCount.set(0);
        lastStateChangedTime.set(System.currentTimeMillis());
        log.info("Circuit breaker '{}' manually reset to CLOSED", name);
    }
}
