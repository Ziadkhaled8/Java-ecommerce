package com.ecommerce.dreamstore.repository;

import com.ecommerce.dreamstore.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
