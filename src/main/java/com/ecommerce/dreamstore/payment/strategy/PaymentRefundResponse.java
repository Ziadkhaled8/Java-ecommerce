package com.ecommerce.dreamstore.payment.strategy;

import java.math.BigDecimal;

public record PaymentRefundResponse(
        String refundId,
        String externalTransactionId,
        BigDecimal amountRefunded,
        String status
) {
}
