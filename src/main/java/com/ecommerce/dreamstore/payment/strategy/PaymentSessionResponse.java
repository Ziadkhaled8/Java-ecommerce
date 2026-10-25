package com.ecommerce.dreamstore.payment.strategy;

public record PaymentSessionResponse(
        String externalTransactionId,
        String checkoutUrl,
        String status
) {
}
