package com.ecommerce.dreamstore.dto.response;

public record CartItemResponse(
        Long id,
        ProductResponse product,
        int quantity
) {
}
