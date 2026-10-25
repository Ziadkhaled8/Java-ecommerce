package com.ecommerce.dreamstore.mapper;

import com.ecommerce.dreamstore.dto.response.PaymentResponse;
import com.ecommerce.dreamstore.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        Long orderId = payment.getOrder() != null ? payment.getOrder().getId() : null;

        return new PaymentResponse(
                payment.getId(),
                orderId,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getState(),
                payment.getIdempotencyKey(),
                payment.getExternalTransactionId(),
                payment.getCheckoutUrl(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
