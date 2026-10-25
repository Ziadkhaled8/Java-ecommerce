package com.ecommerce.dreamstore.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PaginationRequest(
        @Min(value = 0, message = "Page index must not be less than zero")
        Integer page,

        @Min(value = 1, message = "Page size must not be less than one")
        @Max(value = 100, message = "Page size must not be greater than 100")
        Integer size
) {
    public PaginationRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 10;
        }
    }
}
