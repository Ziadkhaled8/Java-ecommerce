package com.ecommerce.dreamstore.dto.response;

import java.util.List;

public record CartResponse(
    Long id,
    Double totalPrice,
    List<CartItemResponse> items
) {
}
