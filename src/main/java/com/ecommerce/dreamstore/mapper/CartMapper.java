package com.ecommerce.dreamstore.mapper;

import com.ecommerce.dreamstore.dto.response.CartItemResponse;
import com.ecommerce.dreamstore.dto.response.CartResponse;
import com.ecommerce.dreamstore.entity.Cart;
import com.ecommerce.dreamstore.entity.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CartMapper {

    private final ProductMapper productMapper;

    public CartResponse toResponse(Cart cart) {
        if (cart == null) {
            return null;
        }

        List<CartItemResponse> itemResponses = cart.getItems() == null
                ? Collections.emptyList()
                : cart.getItems().stream()
                        .map(this::toItemResponse)
                        .toList();

        double totalPrice = cart.getItems() == null
                ? 0.0
                : cart.getItems().stream()
                        .mapToDouble(item -> {
                            BigDecimal price = item.getPrice() != null
                                    ? item.getPrice()
                                    : (item.getProduct() != null ? item.getProduct().getPrice() : BigDecimal.ZERO);
                            return price != null ? price.doubleValue() * item.getQuantity() : 0.0;
                        })
                        .sum();

        return new CartResponse(
                cart.getId(),
                totalPrice,
                itemResponses
        );
    }

    public CartItemResponse toItemResponse(CartItem item) {
        if (item == null) {
            return null;
        }
        return new CartItemResponse(
                item.getId(),
                item.getProduct() != null ? productMapper.toResponse(item.getProduct()) : null,
                item.getQuantity()
        );
    }
}
