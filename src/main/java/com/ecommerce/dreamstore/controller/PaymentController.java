package com.ecommerce.dreamstore.controller;

import com.ecommerce.dreamstore.dto.request.InitiatePaymentRequest;
import com.ecommerce.dreamstore.dto.response.PaymentResponse;
import com.ecommerce.dreamstore.service.interfaces.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/checkout")
    public ResponseEntity<PaymentResponse> initiateCheckout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        String effectiveKey = (idempotencyHeader != null && !idempotencyHeader.isBlank())
                ? idempotencyHeader.trim()
                : request.idempotencyKey();

        if (effectiveKey == null || effectiveKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required (provide 'Idempotency-Key' header or 'idempotencyKey' in body)");
        }

        InitiatePaymentRequest effectiveRequest = new InitiatePaymentRequest(
                request.orderId(),
                request.provider(),
                effectiveKey,
                request.successUrl(),
                request.cancelUrl()
        );

        PaymentResponse response = paymentService.initiatePayment(effectiveRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getLatestPaymentByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getLatestPaymentByOrderId(orderId));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<PaymentResponse> syncPaymentStatus(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.syncPaymentStatus(id));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader
    ) {
        log.info("Incoming Stripe webhook notification");
        paymentService.handleStripeWebhook(payload, signatureHeader);
        return ResponseEntity.ok("Received");
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> processRefund(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Requested by user/admin") String reason
    ) {
        return ResponseEntity.ok(paymentService.processRefund(id, reason));
    }
}
