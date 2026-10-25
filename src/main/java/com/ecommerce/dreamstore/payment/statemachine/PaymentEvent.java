package com.ecommerce.dreamstore.payment.statemachine;

public enum PaymentEvent {
    INITIATE,
    AUTHORIZE,
    COMPLETE,
    FAIL,
    CANCEL,
    REFUND
}
