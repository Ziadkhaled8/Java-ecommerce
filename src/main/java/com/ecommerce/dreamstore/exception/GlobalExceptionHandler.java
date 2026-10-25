package com.ecommerce.dreamstore.exception;

import com.ecommerce.dreamstore.config.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CategoryHasProductsException.class)
    public ProblemDetail handleCategoryHasProducts(CategoryHasProductsException ex) {
        log.warn("Category conflict: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ProblemDetail handleTokenRefreshException(TokenRefreshException ex) {
        log.warn("Token refresh failed: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "Invalid email/phone or password");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ProblemDetail handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("Username not found: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failure: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.FORBIDDEN, "Access denied: " + ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation failed: {}", detail);
        return buildProblemDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.CONFLICT,
                "Resource was modified concurrently. Please refresh and try again.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    public ProblemDetail handleCircuitBreakerOpen(CircuitBreakerOpenException ex) {
        log.error("Circuit breaker fast-fail: {}", ex.getMessage());
        ProblemDetail problemDetail = buildProblemDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problemDetail.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        problemDetail.setProperty("circuitBreaker", ex.getCircuitBreakerName());
        return problemDetail;
    }

    @ExceptionHandler(InvalidPaymentStateTransitionException.class)
    public ProblemDetail handleInvalidPaymentStateTransition(InvalidPaymentStateTransitionException ex) {
        log.warn("Invalid payment state transition: {}", ex.getMessage());
        ProblemDetail problemDetail = buildProblemDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setProperty("fromState", ex.getFromState());
        problemDetail.setProperty("event", ex.getEvent());
        return problemDetail;
    }

    @ExceptionHandler(PaymentException.class)
    public ProblemDetail handlePaymentException(PaymentException ex) {
        log.error("Payment error: {}", ex.getMessage(), ex);
        return buildProblemDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    /**
     * Builds a {@link ProblemDetail} and attaches the current correlation ID
     * so clients can reference it when reporting issues.
     */
    private ProblemDetail buildProblemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        if (correlationId != null) {
            problemDetail.setProperty("correlationId", correlationId);
        }
        return problemDetail;
    }
}

