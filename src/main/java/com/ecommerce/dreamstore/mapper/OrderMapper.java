package com.ecommerce.dreamstore.mapper;

import com.ecommerce.dreamstore.dto.response.OrderItemResponse;
import com.ecommerce.dreamstore.dto.response.OrderResponse;
import com.ecommerce.dreamstore.entity.Order;
import com.ecommerce.dreamstore.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> itemResponses = order.getItems() == null
                ? Collections.emptyList()
                : order.getItems().stream()
                        .map(this::toItemResponse)
                        .toList();

        Double totalPrice = order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0;

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.isPaid(),
                totalPrice,
                order.getShippingAddress(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }

        String productName = item.getProduct() != null ? item.getProduct().getName() : null;
        double price = item.getPrice() != null ? item.getPrice().doubleValue() : 0.0;

        return new OrderItemResponse(
                item.getId(),
                productName,
                price,
                item.getQuantity()
        );
    }
}
