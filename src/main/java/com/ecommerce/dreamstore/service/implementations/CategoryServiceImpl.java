package com.ecommerce.dreamstore.service.implementations;

import com.ecommerce.dreamstore.dto.request.CreateCategoryRequest;
import com.ecommerce.dreamstore.dto.request.UpdateCategoryRequest;
import com.ecommerce.dreamstore.dto.response.CategoryResponse;
import com.ecommerce.dreamstore.entity.Category;
import com.ecommerce.dreamstore.exception.CategoryHasProductsException;
import com.ecommerce.dreamstore.exception.NotFoundException;
import com.ecommerce.dreamstore.mapper.CategoryMapper;
import com.ecommerce.dreamstore.repository.CategoryRepository;
import com.ecommerce.dreamstore.repository.ProductRepository;
import com.ecommerce.dreamstore.service.interfaces.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        Category category = categoryMapper.toEntity(request);
        category = categoryRepository.save(category);
        return categoryMapper.toResponse(category, 0L);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = findCategoryById(id);
        categoryMapper.applyUpdate(request, category);
        category = categoryRepository.save(category);
        long productCount = productRepository.countByCategoryId(id);
        return categoryMapper.toResponse(category, productCount);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategoryById(id);

        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new CategoryHasProductsException(id, (int) productCount);
        }

        categoryRepository.delete(category);
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found with id: " + id));
    }
}
