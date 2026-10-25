package com.ecommerce.dreamstore.controller;

import com.ecommerce.dreamstore.dto.request.CreateOrderRequest;
import com.ecommerce.dreamstore.dto.request.PaginationRequest;
import com.ecommerce.dreamstore.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.dreamstore.dto.response.OrderResponse;
import com.ecommerce.dreamstore.service.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        String effectiveKey = (idempotencyHeader != null && !idempotencyHeader.isBlank())
                ? idempotencyHeader.trim()
                : request.idempotencyKey();

        if (effectiveKey == null || effectiveKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required (provide 'Idempotency-Key' header or 'idempotencyKey' in body)");
        }

        CreateOrderRequest effectiveRequest = new CreateOrderRequest(request.shippingAddress(), effectiveKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(effectiveRequest));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getAllUserOrders(
            @Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(orderService.getAllUserOrders(pagination.page(), pagination.size(), status));
    }

    @GetMapping("/all")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @Valid @ModelAttribute PaginationRequest pagination,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(orderService.getAllOrders(pagination.page(), pagination.size(), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        orderService.updateOrderStatus(id, request.status().name());
        return ResponseEntity.noContent().build();
    }
}
