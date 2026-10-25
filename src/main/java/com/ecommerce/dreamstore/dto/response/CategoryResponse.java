package com.ecommerce.dreamstore.dto.response;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        int productCount
) {
}
