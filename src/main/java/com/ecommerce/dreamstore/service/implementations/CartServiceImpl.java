package com.ecommerce.dreamstore.service.implementations;

import com.ecommerce.dreamstore.dto.response.CartResponse;
import com.ecommerce.dreamstore.entity.Cart;
import com.ecommerce.dreamstore.entity.CartItem;
import com.ecommerce.dreamstore.entity.Product;
import com.ecommerce.dreamstore.exception.NotFoundException;
import com.ecommerce.dreamstore.mapper.CartMapper;
import com.ecommerce.dreamstore.repository.CartRepository;
import com.ecommerce.dreamstore.repository.ProductRepository;
import com.ecommerce.dreamstore.security.AuthenticatedUserService;
import com.ecommerce.dreamstore.service.interfaces.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartMapper cartMapper;
    private final AuthenticatedUserService authenticatedUserService;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart() {
        Cart cart = getCartForCurrentUser();
        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart() {
        Cart cart = getCartForCurrentUser();
        cart.clearItems();
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void addProductToCart(Long productId, Integer quantity) {
        int qtyToAdd = (quantity == null || quantity <= 0) ? 1 : quantity;

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + productId));

        Cart cart = getCartForCurrentUser();

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(null);

        int totalDesiredQuantity = (existingItem != null ? existingItem.getQuantity() : 0) + qtyToAdd;

        if (product.getStockQuantity() < totalDesiredQuantity) {
            throw new IllegalArgumentException("Insufficient stock for product '" + product.getName()
                    + "'. Available: " + product.getStockQuantity() + ", requested: " + totalDesiredQuantity);
        }

        if (existingItem != null) {
            existingItem.setQuantity(totalDesiredQuantity);
            existingItem.setPrice(product.getPrice());
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(qtyToAdd);
            newItem.setPrice(product.getPrice());
            cart.addItem(newItem);
        }

        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void removeProductFromCart(Long productId) {
        Cart cart = getCartForCurrentUser();
        CartItem itemToRemove = cart.getItems().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Product with id " + productId + " not found in cart"));

        cart.removeItem(itemToRemove);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateCartItemQuantity(Long cartItemId, Integer quantity) {
        Cart cart = getCartForCurrentUser();
        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item not found with id: " + cartItemId));

        if (quantity == null || quantity <= 0) {
            cart.removeItem(item);
            cartRepository.save(cart);
            return;
        }

        Product product = item.getProduct();
        if (product != null && product.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient stock for product '" + product.getName()
                    + "'. Available: " + product.getStockQuantity() + ", requested: " + quantity);
        }

        item.setQuantity(quantity);
        if (product != null) {
            item.setPrice(product.getPrice());
        }
        cartRepository.save(cart);
    }

    private Cart getCartForCurrentUser() {
        UUID userId = authenticatedUserService.getCurrentUserId();
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Cart not found for user: " + userId));
    }
}
