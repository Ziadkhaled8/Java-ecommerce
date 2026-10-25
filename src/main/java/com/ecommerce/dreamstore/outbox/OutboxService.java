package com.ecommerce.dreamstore.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to store domain events in the outbox table within the caller's active transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage saveEvent(String aggregateType, String aggregateId, String eventType, Object payloadObject) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payloadObject);
            OutboxMessage message = OutboxMessage.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();

            OutboxMessage saved = outboxMessageRepository.save(message);
            log.info("Persisted OutboxMessage: id={}, aggregateType={}, aggregateId={}, eventType={}",
                    saved.getId(), aggregateType, aggregateId, eventType);

            // Publish local event which will trigger AFTER_COMMIT async publishing
            eventPublisher.publishEvent(new OutboxMessageCreatedEvent(saved.getId()));

            return saved;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
