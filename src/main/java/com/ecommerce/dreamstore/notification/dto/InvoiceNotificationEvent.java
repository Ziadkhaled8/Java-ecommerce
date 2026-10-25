package com.ecommerce.dreamstore.notification.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Self-contained notification event contract for invoice dispatching.
 * Completely independent of database entity models to allow standalone consumption.
 */
public record InvoiceNotificationEvent(
        Long orderId,
        Long paymentId,
        String transactionId,
        String recipientEmail,
        String recipientName,
        BigDecimal amount,
        String currency,
        Date paidAt,
        String shippingAddress,
        List<InvoiceItemDto> items
) {
}
