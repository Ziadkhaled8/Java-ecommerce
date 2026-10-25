package com.ecommerce.dreamstore.service.interfaces;

import com.ecommerce.dreamstore.dto.request.CreateProductRequest;
import com.ecommerce.dreamstore.dto.request.ProductFilter;
import com.ecommerce.dreamstore.dto.request.UpdateProductRequest;
import com.ecommerce.dreamstore.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    ProductResponse getProductById(Long id);
    Page<ProductResponse> getAllProducts(ProductFilter filter, Pageable pageable);
    ProductResponse createProduct(CreateProductRequest request);
    ProductResponse updateProduct(Long id, UpdateProductRequest request);
    void deleteProductImage(Long productId, Long imageId);
    void deleteProduct(Long id);
}
