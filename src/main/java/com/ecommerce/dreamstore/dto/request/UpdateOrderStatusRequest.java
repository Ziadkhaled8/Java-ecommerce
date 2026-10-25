package com.ecommerce.dreamstore.dto.request;

import com.ecommerce.dreamstore.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Order status is required")
        OrderStatus status
) {
}
