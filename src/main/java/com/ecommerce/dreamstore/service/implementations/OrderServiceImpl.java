package com.ecommerce.dreamstore.service.implementations;

import com.ecommerce.dreamstore.dto.request.CreateOrderRequest;
import com.ecommerce.dreamstore.dto.response.OrderResponse;
import com.ecommerce.dreamstore.entity.*;
import com.ecommerce.dreamstore.exception.NotFoundException;
import com.ecommerce.dreamstore.mapper.OrderMapper;
import com.ecommerce.dreamstore.repository.CartRepository;
import com.ecommerce.dreamstore.repository.OrderRepository;
import com.ecommerce.dreamstore.repository.ProductRepository;
import com.ecommerce.dreamstore.security.AuthenticatedUserService;
import com.ecommerce.dreamstore.service.interfaces.OrderService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final int MAX_RETRIES = 3;

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final AuthenticatedUserService authenticatedUserService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        User user = authenticatedUserService.getCurrentUser();

        // Idempotency check — return the existing order if this key was already used
        Optional<Order> existingOrder = orderRepository
                .findByIdempotencyKeyAndUserId(request.idempotencyKey(), user.getId());
        if (existingOrder.isPresent()) {
            log.info("Duplicate order creation detected for idempotencyKey='{}', returning existing order id={}",
                    request.idempotencyKey(), existingOrder.get().getId());
            return orderMapper.toResponse(existingOrder.get());
        }

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Retry loop for optimistic locking conflicts on product stock
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return txTemplate.execute(status -> doCreateOrder(request, user));
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic lock conflict on attempt {}/{} for idempotencyKey='{}': {}",
                        attempt, MAX_RETRIES, request.idempotencyKey(), ex.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw ex;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Order creation was interrupted", ie);
                }
            } catch (DataIntegrityViolationException ex) {
                // Race condition: another thread inserted the same idempotency key concurrently
                log.info("Concurrent duplicate insert for idempotencyKey='{}', fetching existing order",
                        request.idempotencyKey());
                return orderRepository
                        .findByIdempotencyKeyAndUserId(request.idempotencyKey(), user.getId())
                        .map(orderMapper::toResponse)
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency conflict detected but existing order not found"));
            }
        }

        // Unreachable, but the compiler needs it
        throw new IllegalStateException("Unexpected state in order creation");
    }

    private OrderResponse doCreateOrder(CreateOrderRequest request, User user) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Cart not found for user"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot place an order with an empty cart");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(request.shippingAddress());
        order.setIdempotencyKey(request.idempotencyKey());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            if (cartItem.getProduct() == null) {
                continue;
            }

            Long productId = cartItem.getProduct().getId();

            // Regular fetch — optimistic locking via @Version on Product handles concurrency
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found with id: " + productId));

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for product '" + product.getName()
                        + "'. Available: " + product.getStockQuantity() + ", requested: " + cartItem.getQuantity());
            }

            // Deduct stock — will trigger OptimisticLockException if version changed
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            BigDecimal itemPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .price(itemPrice)
                    .build();

            orderItems.add(orderItem);

            BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalPrice = totalPrice.add(itemTotal);
        }

        order.setItems(orderItems);
        order.setTotalPrice(totalPrice);

        order = orderRepository.save(order);

        // Clear the user's cart after successful order creation
        cart.clearItems();
        cartRepository.save(cart);

        // Explicitly flush to verify optimistic lock version checking within this transaction
        productRepository.flush();
        orderRepository.flush();
        cartRepository.flush();

        log.info("Order created: id={}, idempotencyKey='{}', totalPrice={}",
                order.getId(), order.getIdempotencyKey(), order.getTotalPrice());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllUserOrders(int page, int size, @Nullable String status) {
        User user = authenticatedUserService.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = parseOrderStatus(status);
            orders = orderRepository.findByUserIdAndStatus(user.getId(), orderStatus, pageable);
        } else {
            orders = orderRepository.findByUserId(user.getId(), pageable);
        }

        return orders.map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(int page, int size, @Nullable String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = parseOrderStatus(status);
            orders = orderRepository.findByStatus(orderStatus, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));

        User currentUser = authenticatedUserService.getCurrentUser();
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You are not authorized to view this order");
        }

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, String status) {
        OrderStatus newStatus = parseOrderStatus(status);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found with id: " + orderId));

        // If cancelling an order, restore stock (optimistic locking protects concurrency)
        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    if (item.getProduct() != null) {
                        Product product = productRepository.findById(item.getProduct().getId())
                                .orElse(item.getProduct());
                        product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                        productRepository.save(product);
                    }
                }
            }
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        log.info("Order id={} status updated to {}", orderId, newStatus);
    }

    private OrderStatus parseOrderStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid order status '" + status + "'. Allowed values: "
                    + Arrays.toString(OrderStatus.values()));
        }
    }
}
