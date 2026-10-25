package com.ecommerce.dreamstore.outbox;

import com.ecommerce.dreamstore.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Shared publisher component for outbox messages.
 * Used by both immediate asynchronous event listeners and the background fallback poller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${outbox.poller.max-retries:5}")
    private int maxRetries;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(UUID messageId) {
        outboxMessageRepository.findById(messageId).ifPresent(this::publishMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishMessage(OutboxMessage message) {
        // Guard against duplicate publishing
        if (message.getStatus() == OutboxStatus.PROCESSED) {
            log.debug("Outbox message id={} is already PROCESSED. Skipping.", message.getId());
            return;
        }

        String exchange = RabbitMqConfig.ORDER_EVENTS_EXCHANGE;
        String routingKey = resolveRoutingKey(message.getEventType());

        try {
            Message amqpMessage = MessageBuilder
                    .withBody(message.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setHeader("aggregateType", message.getAggregateType())
                    .setHeader("aggregateId", message.getAggregateId())
                    .setHeader("eventType", message.getEventType())
                    .setHeader("outboxId", message.getId().toString())
                    .build();

            rabbitTemplate.send(exchange, routingKey, amqpMessage);

            message.setStatus(OutboxStatus.PROCESSED);
            message.setProcessedAt(new Date());
            message.setErrorMessage(null);
            outboxMessageRepository.save(message);

            log.info("Successfully published outbox message id={} [eventType='{}'] to exchange='{}'",
                    message.getId(), message.getEventType(), exchange);
        } catch (Exception ex) {
            int retries = message.getRetryCount() + 1;
            message.setRetryCount(retries);
            message.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());

            if (retries >= maxRetries) {
                message.setStatus(OutboxStatus.FAILED);
                log.error("Outbox message id={} reached max retries ({}/{}). Marked as FAILED: {}",
                        message.getId(), retries, maxRetries, ex.getMessage());
            } else {
                log.warn("Failed to publish outbox message id={} (attempt {}/{}). Will be picked up by fallback poller: {}",
                        message.getId(), retries, maxRetries, ex.getMessage());
            }

            outboxMessageRepository.save(message);
        }
    }

    private String resolveRoutingKey(String eventType) {
        if ("ORDER_PAYMENT_COMPLETED".equalsIgnoreCase(eventType)) {
            return RabbitMqConfig.ROUTING_KEY_PAYMENT_COMPLETED;
        }
        return "order." + eventType.toLowerCase();
    }
}
