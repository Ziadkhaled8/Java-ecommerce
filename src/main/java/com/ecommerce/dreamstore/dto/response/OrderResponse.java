package com.ecommerce.dreamstore.dto.response;

import com.ecommerce.dreamstore.entity.OrderStatus;

import java.util.Date;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        boolean isPaid,
        Double totalPrice,
        String shippingAddress,
        Date createdAt,
        List<OrderItemResponse> items
) {
}
