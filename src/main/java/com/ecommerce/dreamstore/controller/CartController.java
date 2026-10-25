package com.ecommerce.dreamstore.controller;

import com.ecommerce.dreamstore.dto.request.AddToCartRequest;
import com.ecommerce.dreamstore.dto.request.UpdateCartItemRequest;
import com.ecommerce.dreamstore.dto.response.CartResponse;
import com.ecommerce.dreamstore.service.interfaces.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        return ResponseEntity.ok(cartService.getCart());
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addProductToCart(@Valid @RequestBody AddToCartRequest request) {
        cartService.addProductToCart(request.productId(), request.quantity());
        return ResponseEntity.ok(cartService.getCart());
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<CartResponse> updateCartItemQuantity(
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        cartService.updateCartItemQuantity(cartItemId, request.quantity());
        return ResponseEntity.ok(cartService.getCart());
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeProductFromCart(@PathVariable Long productId) {
        cartService.removeProductFromCart(productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart();
        return ResponseEntity.noContent().build();
    }
}

