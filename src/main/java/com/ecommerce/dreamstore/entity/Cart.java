package com.ecommerce.dreamstore.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();
    @OneToOne( fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    public void addItem(CartItem item) {
        this.items.add(item);
    }
    public void removeItem(CartItem item) {
        this.items.remove(item);
    }
    public void clearItems() {
        this.items.clear();
    }
    public void updateItemQuantity(CartItem item, int quantity) {
        item.setQuantity(quantity);
        CartItem existingItem = this.items.stream()
                .filter(i -> i.getId().equals(item.getId()))
                .findFirst()
                .orElse(null);
        if (existingItem != null) {
            existingItem.setQuantity(quantity);
        }
        else {
            this.items.add(item);
        }
    }
}
