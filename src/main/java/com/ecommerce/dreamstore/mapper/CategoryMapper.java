package com.ecommerce.dreamstore.mapper;

import com.ecommerce.dreamstore.dto.request.CreateCategoryRequest;
import com.ecommerce.dreamstore.dto.request.UpdateCategoryRequest;
import com.ecommerce.dreamstore.dto.response.CategoryResponse;
import com.ecommerce.dreamstore.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toEntity(CreateCategoryRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return category;
    }

    /**
     * Applies non-null fields from {@link UpdateCategoryRequest} onto an
     * existing {@link Category} entity (patch semantics).
     */
    public void applyUpdate(UpdateCategoryRequest request, Category category) {
        if (request.name() != null)        category.setName(request.name());
        if (request.description() != null) category.setDescription(request.description());
    }

    /**
     * Converts a {@link Category} to a response DTO.
     *
     * @param productCount pre-fetched count from the repository — avoids triggering
     *                     the lazy-loaded {@code products} collection.
     */
    public CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                (int) productCount
        );
    }
}
