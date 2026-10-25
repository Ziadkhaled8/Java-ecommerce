package com.ecommerce.dreamstore.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxStatus status,
            int maxRetries,
            Pageable pageable
    );
}
