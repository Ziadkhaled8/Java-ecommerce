package com.ecommerce.dreamstore.repository;

import com.ecommerce.dreamstore.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Issues {@code SELECT COUNT(*) FROM product WHERE category_id = ?} — no product rows loaded. */
    long countByCategoryId(Long categoryId);
}
