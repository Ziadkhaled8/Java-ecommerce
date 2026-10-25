package com.ecommerce.dreamstore.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Background fallback poller that sweeps for any pending outbox messages that
 * were not sent by the immediate AFTER_COMMIT async dispatcher (e.g., due to temporary
 * network interruptions or system restarts).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxPublisher outboxPublisher;

    @Value("${outbox.poller.batch-size:20}")
    private int batchSize;

    @Value("${outbox.poller.max-retries:5}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:30000}")
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxMessageRepository
                .findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        maxRetries,
                        PageRequest.of(0, batchSize)
                );

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("Fallback poller picked up {} pending outbox message(s) to publish", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            outboxPublisher.publishMessage(message);
        }
    }
}
