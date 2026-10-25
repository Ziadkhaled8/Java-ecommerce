package com.ecommerce.dreamstore.dto.response;

import com.ecommerce.dreamstore.entity.PaymentProvider;
import com.ecommerce.dreamstore.entity.PaymentState;

import java.math.BigDecimal;
import java.util.Date;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String currency,
        PaymentProvider provider,
        PaymentState state,
        String idempotencyKey,
        String externalTransactionId,
        String checkoutUrl,
        String failureReason,
        Date createdAt,
        Date updatedAt
) {
}
