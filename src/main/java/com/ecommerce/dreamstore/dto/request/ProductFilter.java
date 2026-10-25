package com.ecommerce.dreamstore.dto.request;

import java.math.BigDecimal;

public record ProductFilter(String name, Long categoryId,
                            String brand, BigDecimal minPrice, BigDecimal maxPrice) {
}
