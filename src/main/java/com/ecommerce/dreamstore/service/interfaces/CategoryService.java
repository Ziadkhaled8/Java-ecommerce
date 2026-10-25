package com.ecommerce.dreamstore.service.interfaces;

import com.ecommerce.dreamstore.dto.request.CreateCategoryRequest;
import com.ecommerce.dreamstore.dto.request.UpdateCategoryRequest;
import com.ecommerce.dreamstore.dto.response.CategoryResponse;

public interface CategoryService {
    CategoryResponse createCategory(CreateCategoryRequest request);
    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);
    void deleteCategory(Long id);
}
