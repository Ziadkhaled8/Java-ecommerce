package com.ecommerce.dreamstore.notification.dto;

import java.math.BigDecimal;

public record InvoiceItemDto(
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
}
