package com.ecommerce.dreamstore.exception;

import com.ecommerce.dreamstore.entity.PaymentState;
import com.ecommerce.dreamstore.payment.statemachine.PaymentEvent;

public class InvalidPaymentStateTransitionException extends PaymentException {

    private final PaymentState fromState;
    private final PaymentEvent event;

    public InvalidPaymentStateTransitionException(PaymentState fromState, PaymentEvent event) {
        super(String.format("Invalid payment state transition: cannot execute event '%s' from state '%s'", event, fromState));
        this.fromState = fromState;
        this.event = event;
    }

    public PaymentState getFromState() {
        return fromState;
    }

    public PaymentEvent getEvent() {
        return event;
    }
}
