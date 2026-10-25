package com.ecommerce.dreamstore.payment.strategy;

import com.ecommerce.dreamstore.entity.PaymentProvider;

import java.math.BigDecimal;

/**
 * Strategy interface defining the contract for all external payment providers (Stripe, PayPal, Mock, etc.).
 */
public interface PaymentStrategy {

    /**
     * Identifies the provider implemented by this strategy.
     */
    PaymentProvider getProvider();

    /**
     * Creates a hosted checkout session with the external payment gateway.
     */
    PaymentSessionResponse createCheckoutSession(PaymentSessionContext context);

    /**
     * Checks/synchronizes transaction status with the external gateway.
     */
    PaymentStatusResponse verifyPayment(String externalTransactionId);

    /**
     * Refunds an existing payment with the external gateway.
     */
    PaymentRefundResponse processRefund(String externalTransactionId, BigDecimal amount, String reason);
}
