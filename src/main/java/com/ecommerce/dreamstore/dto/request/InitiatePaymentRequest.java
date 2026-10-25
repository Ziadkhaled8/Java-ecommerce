package com.ecommerce.dreamstore.dto.request;

import com.ecommerce.dreamstore.entity.PaymentProvider;
import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Order ID is required")
        Long orderId,

        PaymentProvider provider,

        String idempotencyKey,

        String successUrl,

        String cancelUrl
) {
}
