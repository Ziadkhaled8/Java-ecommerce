package com.ecommerce.dreamstore.service.interfaces;

import com.ecommerce.dreamstore.dto.request.CreateOrderRequest;
import com.ecommerce.dreamstore.dto.response.OrderResponse;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;

public interface OrderService {
    Page<OrderResponse> getAllUserOrders(int page, int size, @Nullable String status);
    Page<OrderResponse> getAllOrders(int page, int size, @Nullable String status);
    OrderResponse getOrderById(Long orderId);
    OrderResponse createOrder(CreateOrderRequest request);
    void updateOrderStatus(Long orderId, String status);
}

