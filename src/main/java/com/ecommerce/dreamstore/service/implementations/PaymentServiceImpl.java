package com.ecommerce.dreamstore.service.implementations;

import com.ecommerce.dreamstore.dto.request.InitiatePaymentRequest;
import com.ecommerce.dreamstore.dto.response.PaymentResponse;
import com.ecommerce.dreamstore.entity.*;
import com.ecommerce.dreamstore.exception.NotFoundException;
import com.ecommerce.dreamstore.exception.PaymentException;
import com.ecommerce.dreamstore.mapper.PaymentMapper;
import com.ecommerce.dreamstore.payment.statemachine.PaymentEvent;
import com.ecommerce.dreamstore.payment.statemachine.PaymentStateMachine;
import com.ecommerce.dreamstore.payment.strategy.PaymentSessionContext;
import com.ecommerce.dreamstore.payment.strategy.PaymentSessionResponse;
import com.ecommerce.dreamstore.payment.strategy.PaymentStatusResponse;
import com.ecommerce.dreamstore.payment.strategy.PaymentStrategy;
import com.ecommerce.dreamstore.payment.strategy.PaymentStrategyRegistry;
import com.ecommerce.dreamstore.repository.OrderRepository;
import com.ecommerce.dreamstore.repository.PaymentRepository;
import com.ecommerce.dreamstore.security.AuthenticatedUserService;
import com.ecommerce.dreamstore.service.interfaces.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentStrategyRegistry strategyRegistry;
    private final PaymentStateMachine stateMachine;
    private final PaymentMapper paymentMapper;
    private final AuthenticatedUserService authenticatedUserService;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret:whsec_placeholder}")
    private String webhookSecret;

    @Value("${stripe.success-url:http://localhost:9193/api/payments/success?session_id={CHECKOUT_SESSION_ID}}")
    private String defaultSuccessUrl;

    @Value("${stripe.cancel-url:http://localhost:9193/api/payments/cancel?order_id={ORDER_ID}}")
    private String defaultCancelUrl;

    @Value("${stripe.currency:USD}")
    private String defaultCurrency;

    @Override
    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {
        User user = authenticatedUserService.getCurrentUser();

        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + request.orderId()));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not authorized to pay for this order");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not in PENDING status. Current status: " + order.getStatus());
        }

        String idempotencyKey = request.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        // Idempotency check: Return existing payment if this key was already submitted for this user
        Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKeyAndUserId(idempotencyKey, user.getId());
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            log.info("Duplicate payment request detected for idempotencyKey='{}', returning existing payment id={}",
                    idempotencyKey, existing.getId());
            return paymentMapper.toResponse(existing);
        }

        PaymentProvider provider = request.provider() != null ? request.provider() : PaymentProvider.STRIPE;
        PaymentStrategy strategy = strategyRegistry.getStrategy(provider);

        // Build itemized line items from Order items
        List<PaymentSessionContext.PaymentLineItem> lineItems = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                String name = item.getProduct() != null ? item.getProduct().getName() : "Order Item";
                String description = item.getProduct() != null ? item.getProduct().getDescription() : null;
                BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                lineItems.add(new PaymentSessionContext.PaymentLineItem(name, description, item.getQuantity(), price));
            }
        }

        String successUrl = (request.successUrl() != null && !request.successUrl().isBlank())
                ? request.successUrl()
                : defaultSuccessUrl.replace("{ORDER_ID}", String.valueOf(order.getId()));

        String cancelUrl = (request.cancelUrl() != null && !request.cancelUrl().isBlank())
                ? request.cancelUrl()
                : defaultCancelUrl.replace("{ORDER_ID}", String.valueOf(order.getId()));

        PaymentSessionContext context = new PaymentSessionContext(
                order.getId(),
                user.getId(),
                user.getEmail(),
                order.getTotalPrice(),
                defaultCurrency,
                idempotencyKey,
                lineItems,
                successUrl,
                cancelUrl
        );

        // Persist payment entity in PENDING state
        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .amount(order.getTotalPrice())
                .currency(defaultCurrency)
                .provider(provider)
                .state(PaymentState.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        payment = paymentRepository.save(payment);

        // Invoke strategy through Circuit Breaker to create external checkout session
        PaymentSessionResponse sessionResponse = strategy.createCheckoutSession(context);

        payment.setExternalTransactionId(sessionResponse.externalTransactionId());
        payment.setCheckoutUrl(sessionResponse.checkoutUrl());
        payment = paymentRepository.save(payment);

        log.info("Payment session created: paymentId={}, externalTransactionId={}, checkoutUrl={}",
                payment.getId(), payment.getExternalTransactionId(), payment.getCheckoutUrl());

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found with id: " + paymentId));

        User currentUser = authenticatedUserService.getCurrentUser();
        if (!payment.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not authorized to access this payment");
        }

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getLatestPaymentByOrderId(Long orderId) {
        User currentUser = authenticatedUserService.getCurrentUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));

        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not authorized to view payments for this order");
        }

        return paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("No payment found for order id: " + orderId));
    }

    @Override
    @Transactional
    public PaymentResponse syncPaymentStatus(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found with id: " + paymentId));

        User currentUser = authenticatedUserService.getCurrentUser();
        if (!payment.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not authorized to access this payment");
        }

        if (payment.getExternalTransactionId() != null && !payment.getExternalTransactionId().isBlank()) {
            PaymentStrategy strategy = strategyRegistry.getStrategy(payment.getProvider());
            PaymentStatusResponse statusResponse = strategy.verifyPayment(payment.getExternalTransactionId());

            if (statusResponse.mappedState() == PaymentState.COMPLETED && payment.getState() != PaymentState.COMPLETED) {
                stateMachine.transition(payment, PaymentEvent.COMPLETE);
                payment = paymentRepository.save(payment);
            } else if (statusResponse.mappedState() == PaymentState.CANCELLED && payment.getState() != PaymentState.CANCELLED) {
                if (stateMachine.canTransition(payment.getState(), PaymentEvent.CANCEL)) {
                    stateMachine.transition(payment, PaymentEvent.CANCEL, "Cancelled by user");
                    payment = paymentRepository.save(payment);
                }
            }
        }

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        if (webhookSecret != null && !webhookSecret.isBlank() && !webhookSecret.startsWith("whsec_placeholder")) {
            Event event;
            try {
                event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
            } catch (SignatureVerificationException e) {
                log.error("Stripe webhook signature verification failed: {}", e.getMessage());
                throw new PaymentException("Invalid Stripe webhook signature", e);
            }

            log.info("Processing verified Stripe webhook event: id={}, type={}", event.getId(), event.getType());

            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    if (stripeObject instanceof Session session) {
                        handleCheckoutSessionCompleted(session);
                    } else {
                        log.warn("Unable to deserialize checkout.session.completed object");
                    }
                }
                case "checkout.session.expired" -> {
                    if (stripeObject instanceof Session session) {
                        handleCheckoutSessionExpired(session);
                    }
                }
                default -> log.debug("Ignored unhandled Stripe event type: {}", event.getType());
            }
        } else {
            // Development/Test fallback using standard Jackson parser without requiring external Gson
            log.warn("Stripe webhook secret is not configured or in placeholder mode. Parsing event via Jackson without signature verification.");
            try {
                JsonNode root = objectMapper.readTree(payload);
                String eventType = root.path("type").asText();
                String eventId = root.path("id").asText();
                log.info("Processing unverified Stripe webhook event: id={}, type={}", eventId, eventType);

                JsonNode dataObject = root.path("data").path("object");
                String sessionId = dataObject.path("id").asText();
                String idempotencyKey = dataObject.path("metadata").path("idempotencyKey").asText(null);
                String userId = dataObject.path("metadata").path("userId").asText(null);

                if ("checkout.session.completed".equals(eventType)) {
                    processSessionCompleted(sessionId, idempotencyKey, userId);
                } else if ("checkout.session.expired".equals(eventType)) {
                    processSessionExpired(sessionId);
                }
            } catch (Exception e) {
                log.error("Failed to parse Stripe webhook JSON: {}", e.getMessage(), e);
                throw new PaymentException("Failed to parse webhook JSON payload", e);
            }
        }
    }

    @Override
    @Transactional
    public PaymentResponse processRefund(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found with id: " + paymentId));

        if (payment.getState() != PaymentState.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED payments can be refunded. Current state: " + payment.getState());
        }

        PaymentStrategy strategy = strategyRegistry.getStrategy(payment.getProvider());
        strategy.processRefund(payment.getExternalTransactionId(), payment.getAmount(), reason);

        stateMachine.transition(payment, PaymentEvent.REFUND, reason);
        payment = paymentRepository.save(payment);

        log.info("Payment id={} refunded for order id={}", payment.getId(), payment.getOrder().getId());
        return paymentMapper.toResponse(payment);
    }

    private void handleCheckoutSessionCompleted(Session session) {
        String sessionId = session.getId();
        String idempotencyKey = session.getMetadata() != null ? session.getMetadata().get("idempotencyKey") : null;
        String userIdStr = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
        processSessionCompleted(sessionId, idempotencyKey, userIdStr);
    }

    private void handleCheckoutSessionExpired(Session session) {
        processSessionExpired(session.getId());
    }

    private void processSessionCompleted(String sessionId, String idempotencyKey, String userIdStr) {
        Optional<Payment> paymentOpt = paymentRepository.findByExternalTransactionId(sessionId);

        if (paymentOpt.isEmpty() && idempotencyKey != null && userIdStr != null) {
            try {
                paymentOpt = paymentRepository.findByIdempotencyKeyAndUserId(idempotencyKey, UUID.fromString(userIdStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (paymentOpt.isEmpty()) {
            log.error("Received checkout.session.completed for unknown session id={}", sessionId);
            return;
        }

        Payment payment = paymentOpt.get();

        // Idempotency guard for webhooks:
        if (payment.getState() == PaymentState.COMPLETED) {
            log.info("Payment id={} is already COMPLETED. Skipping duplicate webhook processing.", payment.getId());
            return;
        }

        stateMachine.transition(payment, PaymentEvent.COMPLETE);
        paymentRepository.save(payment);
        log.info("Successfully transitioned payment id={} to COMPLETED via webhook", payment.getId());
    }

    private void processSessionExpired(String sessionId) {
        paymentRepository.findByExternalTransactionId(sessionId).ifPresent(payment -> {
            if (stateMachine.canTransition(payment.getState(), PaymentEvent.CANCEL)) {
                stateMachine.transition(payment, PaymentEvent.CANCEL, "Checkout session expired");
                paymentRepository.save(payment);
                log.info("Payment id={} transitioned to CANCELLED via expired session webhook", payment.getId());
            }
        });
    }
}
