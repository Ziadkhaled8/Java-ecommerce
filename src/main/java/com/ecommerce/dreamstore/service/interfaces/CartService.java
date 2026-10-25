package com.ecommerce.dreamstore.service.interfaces;

import com.ecommerce.dreamstore.dto.response.CartResponse;

public interface CartService {
    CartResponse getCart();
    void clearCart();
    void addProductToCart(Long productId, Integer quantity);
    void removeProductFromCart(Long productId);
    void updateCartItemQuantity(Long cartItemId, Integer quantity);
}


