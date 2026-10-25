package com.ecommerce.dreamstore.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String brand,
        String description,
        BigDecimal price,
        int stockQuantity,
        String categoryName,
        List<ImageResponse> images
) {
}
