package com.ecommerce.dreamstore.payment.statemachine;

import com.ecommerce.dreamstore.entity.Order;
import com.ecommerce.dreamstore.entity.OrderStatus;
import com.ecommerce.dreamstore.entity.Payment;
import com.ecommerce.dreamstore.entity.PaymentState;
import com.ecommerce.dreamstore.exception.InvalidPaymentStateTransitionException;
import com.ecommerce.dreamstore.entity.User;
import com.ecommerce.dreamstore.entity.OrderItem;
import com.ecommerce.dreamstore.notification.dto.InvoiceItemDto;
import com.ecommerce.dreamstore.notification.dto.InvoiceNotificationEvent;
import com.ecommerce.dreamstore.outbox.OutboxService;
import com.ecommerce.dreamstore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Domain State Machine for managing Payment lifecycle transitions.
 * Enforces valid transition paths, prevents illegal state corruptions,
 * and executes side-effect actions (e.g., updating Order status on payment completion).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStateMachine {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    // Transition table: Map<CurrentState, Map<Event, NextState>>
    private static final Map<PaymentState, Map<PaymentEvent, PaymentState>> TRANSITIONS;

    static {
        Map<PaymentState, Map<PaymentEvent, PaymentState>> map = new EnumMap<>(PaymentState.class);

        // PENDING transitions
        Map<PaymentEvent, PaymentState> pendingTransitions = new EnumMap<>(PaymentEvent.class);
        pendingTransitions.put(PaymentEvent.COMPLETE, PaymentState.COMPLETED);
        pendingTransitions.put(PaymentEvent.AUTHORIZE, PaymentState.AUTHORIZED);
        pendingTransitions.put(PaymentEvent.FAIL, PaymentState.FAILED);
        pendingTransitions.put(PaymentEvent.CANCEL, PaymentState.CANCELLED);
        map.put(PaymentState.PENDING, Collections.unmodifiableMap(pendingTransitions));

        // AUTHORIZED transitions
        Map<PaymentEvent, PaymentState> authorizedTransitions = new EnumMap<>(PaymentEvent.class);
        authorizedTransitions.put(PaymentEvent.COMPLETE, PaymentState.COMPLETED);
        authorizedTransitions.put(PaymentEvent.FAIL, PaymentState.FAILED);
        authorizedTransitions.put(PaymentEvent.CANCEL, PaymentState.CANCELLED);
        map.put(PaymentState.AUTHORIZED, Collections.unmodifiableMap(authorizedTransitions));

        // COMPLETED transitions
        Map<PaymentEvent, PaymentState> completedTransitions = new EnumMap<>(PaymentEvent.class);
        completedTransitions.put(PaymentEvent.REFUND, PaymentState.REFUNDED);
        map.put(PaymentState.COMPLETED, Collections.unmodifiableMap(completedTransitions));

        // Terminal states have empty transition maps
        map.put(PaymentState.FAILED, Collections.emptyMap());
        map.put(PaymentState.CANCELLED, Collections.emptyMap());
        map.put(PaymentState.REFUNDED, Collections.emptyMap());

        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * Checks whether the given event can be applied to the current state.
     */
    public boolean canTransition(PaymentState currentState, PaymentEvent event) {
        Map<PaymentEvent, PaymentState> allowed = TRANSITIONS.get(currentState);
        return allowed != null && allowed.containsKey(event);
    }

    /**
     * Executes the state transition on the Payment entity, validating legal state
     * progression and executing associated side effects.
     *
     * @param payment the payment entity to transition
     * @param event   the triggering event
     * @return the new state
     */
    public PaymentState transition(Payment payment, PaymentEvent event) {
        return transition(payment, event, null);
    }

    /**
     * Executes the state transition on the Payment entity with an optional reason/error description.
     */
    public PaymentState transition(Payment payment, PaymentEvent event, String reason) {
        PaymentState currentState = payment.getState();
        Map<PaymentEvent, PaymentState> allowed = TRANSITIONS.get(currentState);

        if (allowed == null || !allowed.containsKey(event)) {
            log.error("Illegal payment state transition rejected: paymentId={}, current={}, event={}",
                    payment.getId(), currentState, event);
            throw new InvalidPaymentStateTransitionException(currentState, event);
        }

        PaymentState nextState = allowed.get(event);
        log.info("Payment state transition: paymentId={}, [{} --({})--> {}]",
                payment.getId(), currentState, event, nextState);

        payment.setState(nextState);
        if (reason != null && !reason.isBlank()) {
            payment.setFailureReason(reason);
        }

        executePostTransitionActions(payment, nextState);

        return nextState;
    }

    private void executePostTransitionActions(Payment payment, PaymentState nextState) {
        Order order = payment.getOrder();
        if (order == null) {
            return;
        }

        if (nextState == PaymentState.COMPLETED) {
            order.setPaid(true);
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PROCESSING);
            }
            orderRepository.save(order);
            log.info("Order id={} flagged as PAID and status advanced to {} upon payment completion",
                    order.getId(), order.getStatus());

            createAndSaveInvoiceOutboxMessage(payment, order);
        } else if (nextState == PaymentState.REFUNDED) {
            order.setPaid(false);
            orderRepository.save(order);
            log.info("Order id={} flagged as UNPAID due to payment refund", order.getId());
        }
    }

    private void createAndSaveInvoiceOutboxMessage(Payment payment, Order order) {
        User user = payment.getUser() != null ? payment.getUser() : order.getUser();
        String recipientEmail = user != null ? user.getEmail() : null;
        String recipientName = user != null ? user.getFullName() : null;

        List<InvoiceItemDto> itemDtos = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                String prodName = item.getProduct() != null ? item.getProduct().getName() : "Item";
                BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
                itemDtos.add(new InvoiceItemDto(prodName, item.getQuantity(), unitPrice, totalPrice));
            }
        }

        InvoiceNotificationEvent event = new InvoiceNotificationEvent(
                order.getId(),
                payment.getId(),
                payment.getExternalTransactionId(),
                recipientEmail,
                recipientName,
                payment.getAmount(),
                payment.getCurrency(),
                new Date(),
                order.getShippingAddress(),
                itemDtos
        );

        outboxService.saveEvent("PAYMENT", payment.getId().toString(), "ORDER_PAYMENT_COMPLETED", event);
        log.info("Outbox invoice event created for order id={}", order.getId());
    }
}
