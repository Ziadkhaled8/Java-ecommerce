package com.ecommerce.dreamstore.dto.response;

public record OrderItemResponse(
        Long id,
        String productName,
        double price,
        int quantity
) {
}
