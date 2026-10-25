package com.ecommerce.dreamstore.payment.strategy;

import com.ecommerce.dreamstore.entity.PaymentState;

import java.math.BigDecimal;

public record PaymentStatusResponse(
        String externalTransactionId,
        PaymentState mappedState,
        String rawStatus,
        BigDecimal amount,
        String currency
) {
}
