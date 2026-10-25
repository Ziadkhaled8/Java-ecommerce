package com.ecommerce.dreamstore.repository;

import com.ecommerce.dreamstore.entity.Order;
import com.ecommerce.dreamstore.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);
    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Optional<Order> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);
}
