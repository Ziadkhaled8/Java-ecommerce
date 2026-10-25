package com.ecommerce.dreamstore.outbox;

import java.util.UUID;

/**
 * Spring application event published when an outbox message is saved in a transaction.
 */
public record OutboxMessageCreatedEvent(UUID outboxMessageId) {
}
