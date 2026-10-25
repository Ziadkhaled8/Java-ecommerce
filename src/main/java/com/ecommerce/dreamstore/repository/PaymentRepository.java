package com.ecommerce.dreamstore.repository;

import com.ecommerce.dreamstore.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    Optional<Payment> findByExternalTransactionId(String externalTransactionId);

    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);
}
