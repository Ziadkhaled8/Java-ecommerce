package com.ecommerce.dreamstore.payment.strategy;

import com.ecommerce.dreamstore.entity.PaymentProvider;
import com.ecommerce.dreamstore.entity.PaymentState;
import com.ecommerce.dreamstore.exception.PaymentException;
import com.ecommerce.dreamstore.payment.circuitbreaker.PaymentCircuitBreaker;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Concrete Stripe payment strategy integrating Stripe Checkout Sessions.
 * Protected by {@link PaymentCircuitBreaker} and enforces idempotency via Stripe RequestOptions.
 */
@Component
@Slf4j
public class StripePaymentStrategy implements PaymentStrategy {

    private final String apiKey;
    private final PaymentCircuitBreaker circuitBreaker;

    public StripePaymentStrategy(
            @Value("${stripe.api-key:sk_test_placeholder}") String apiKey,
            PaymentCircuitBreaker circuitBreaker
    ) {
        this.apiKey = apiKey;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public PaymentSessionResponse createCheckoutSession(PaymentSessionContext context) {
        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(context.successUrl())
                .setCancelUrl(context.cancelUrl())
                .setClientReferenceId(String.valueOf(context.orderId()))
                .putMetadata("orderId", String.valueOf(context.orderId()))
                .putMetadata("userId", context.userId().toString())
                .putMetadata("idempotencyKey", context.idempotencyKey());

        if (context.userEmail() != null && !context.userEmail().isBlank()) {
            paramsBuilder.setCustomerEmail(context.userEmail());
        }

        // Add line items
        if (context.items() != null && !context.items().isEmpty()) {
            for (PaymentSessionContext.PaymentLineItem item : context.items()) {
                long unitAmountInCents = item.unitPrice().multiply(BigDecimal.valueOf(100)).longValue();
                paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity((long) item.quantity())
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(context.currency().toLowerCase())
                                                .setUnitAmount(unitAmountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(item.name())
                                                                .setDescription(item.description() != null ? item.description() : item.name())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );
            }
        } else {
            long totalAmountInCents = context.amount().multiply(BigDecimal.valueOf(100)).longValue();
            paramsBuilder.addLineItem(
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                            .setCurrency(context.currency().toLowerCase())
                                            .setUnitAmount(totalAmountInCents)
                                            .setProductData(
                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                            .setName("Order #" + context.orderId())
                                                            .build()
                                            )
                                            .build()
                            )
                            .build()
            );
        }

        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .setIdempotencyKey(context.idempotencyKey())
                .build();

        return circuitBreaker.execute(() -> {
            try {
                log.info("Creating Stripe Checkout Session for orderId={}, idempotencyKey='{}'",
                        context.orderId(), context.idempotencyKey());
                Session session = Session.create(paramsBuilder.build(), requestOptions);
                return new PaymentSessionResponse(session.getId(), session.getUrl(), session.getStatus());
            } catch (StripeException ex) {
                log.error("Stripe Checkout Session creation failed: {}", ex.getMessage());
                throw new PaymentException("Stripe gateway error: " + ex.getMessage(), ex);
            }
        });
    }

    @Override
    public PaymentStatusResponse verifyPayment(String externalTransactionId) {
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .build();

        return circuitBreaker.execute(() -> {
            try {
                Session session = Session.retrieve(externalTransactionId, requestOptions);
                PaymentState mappedState = mapStripeStatus(session);
                BigDecimal amount = session.getAmountTotal() != null
                        ? BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
                return new PaymentStatusResponse(
                        session.getId(),
                        mappedState,
                        session.getStatus(),
                        amount,
                        session.getCurrency() != null ? session.getCurrency().toUpperCase() : "USD"
                );
            } catch (StripeException ex) {
                log.error("Stripe Session retrieval failed for id={}: {}", externalTransactionId, ex.getMessage());
                throw new PaymentException("Failed to verify Stripe payment: " + ex.getMessage(), ex);
            }
        });
    }

    @Override
    public PaymentRefundResponse processRefund(String externalTransactionId, BigDecimal amount, String reason) {
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(apiKey)
                .build();

        return circuitBreaker.execute(() -> {
            try {
                Session session = Session.retrieve(externalTransactionId, requestOptions);
                String paymentIntentId = session.getPaymentIntent();
                if (paymentIntentId == null || paymentIntentId.isBlank()) {
                    throw new PaymentException("No PaymentIntent found on Stripe Checkout Session " + externalTransactionId);
                }

                RefundCreateParams.Builder refundBuilder = RefundCreateParams.builder()
                        .setPaymentIntent(paymentIntentId);

                if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                    refundBuilder.setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue());
                }

                if (reason != null && !reason.isBlank()) {
                    refundBuilder.putMetadata("reason", reason);
                }

                Refund refund = Refund.create(refundBuilder.build(), requestOptions);
                BigDecimal refundedAmount = refund.getAmount() != null
                        ? BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100))
                        : amount;

                return new PaymentRefundResponse(refund.getId(), externalTransactionId, refundedAmount, refund.getStatus());
            } catch (StripeException ex) {
                log.error("Stripe refund failed for session={}: {}", externalTransactionId, ex.getMessage());
                throw new PaymentException("Failed to process Stripe refund: " + ex.getMessage(), ex);
            }
        });
    }

    private PaymentState mapStripeStatus(Session session) {
        String paymentStatus = session.getPaymentStatus();
        if ("paid".equalsIgnoreCase(paymentStatus) || "no_payment_required".equalsIgnoreCase(paymentStatus)) {
            return PaymentState.COMPLETED;
        }

        String sessionStatus = session.getStatus();
        if ("expired".equalsIgnoreCase(sessionStatus)) {
            return PaymentState.CANCELLED;
        }

        return PaymentState.PENDING;
    }
}
