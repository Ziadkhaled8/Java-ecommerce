package com.ecommerce.dreamstore.payment.strategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentSessionContext(
        Long orderId,
        UUID userId,
        String userEmail,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        List<PaymentLineItem> items,
        String successUrl,
        String cancelUrl
) {
    public record PaymentLineItem(
            String name,
            String description,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
