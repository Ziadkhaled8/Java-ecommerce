package com.ecommerce.dreamstore.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener that immediately and asynchronously dispatches outbox messages
 * as soon as the enclosing database transaction successfully commits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventListener {

    private final OutboxPublisher outboxPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxMessageCreated(OutboxMessageCreatedEvent event) {
        log.info("Transaction committed. Immediately dispatching outbox message id={} to message bus asynchronously",
                event.outboxMessageId());
        try {
            outboxPublisher.publish(event.outboxMessageId());
        } catch (Exception ex) {
            log.warn("Immediate dispatch failed for outbox message id={}. Fallback poller will recover it: {}",
                    event.outboxMessageId(), ex.getMessage());
        }
    }
}
