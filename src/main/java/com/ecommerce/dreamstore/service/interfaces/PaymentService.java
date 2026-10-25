package com.ecommerce.dreamstore.service.interfaces;

import com.ecommerce.dreamstore.dto.request.InitiatePaymentRequest;
import com.ecommerce.dreamstore.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse initiatePayment(InitiatePaymentRequest request);

    PaymentResponse getPaymentById(Long paymentId);

    PaymentResponse getLatestPaymentByOrderId(Long orderId);

    PaymentResponse syncPaymentStatus(Long paymentId);

    void handleStripeWebhook(String payload, String signatureHeader);

    PaymentResponse processRefund(Long paymentId, String reason);
}
