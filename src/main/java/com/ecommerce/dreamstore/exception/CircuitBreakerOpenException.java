package com.ecommerce.dreamstore.exception;

public class CircuitBreakerOpenException extends PaymentException {

    private final String circuitBreakerName;
    private final long retryAfterSeconds;

    public CircuitBreakerOpenException(String circuitBreakerName, long retryAfterSeconds) {
        super(String.format("Payment service is temporarily unavailable. Circuit breaker '%s' is OPEN. Please retry in %d seconds.",
                circuitBreakerName, retryAfterSeconds));
        this.circuitBreakerName = circuitBreakerName;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
