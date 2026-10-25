package com.ecommerce.dreamstore.controller;

import com.ecommerce.dreamstore.dto.request.CreateProductRequest;
import com.ecommerce.dreamstore.dto.request.PaginationRequest;
import com.ecommerce.dreamstore.dto.request.ProductFilter;
import com.ecommerce.dreamstore.dto.request.UpdateProductRequest;
import com.ecommerce.dreamstore.dto.response.ProductResponse;
import com.ecommerce.dreamstore.service.interfaces.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService theProductService;

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(theProductService.getProductById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(@ParameterObject ProductFilter filter,
            @Valid @ModelAttribute PaginationRequest pagination) {
        Pageable pageable = PageRequest.of(pagination.page(), pagination.size());
        return ResponseEntity.ok(theProductService.getAllProducts(filter, pageable));
    }

    /**
     * Creates a new product along with optional images.
     * Accepts multipart/form-data so that image files can be submitted alongside
     * product fields.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> createProduct(@Valid @ModelAttribute CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theProductService.createProduct(request));
    }

    /**
     * Updates a product's fields and/or images.
     * Accepts multipart/form-data so that new image files can be included.
     * Only non-null fields are updated (patch semantics).
     * Use {@code removeImageIds} to delete specific existing images by ID.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
            @Valid @ModelAttribute UpdateProductRequest request) {
        return ResponseEntity.ok(theProductService.updateProduct(id, request));
    }

    /**
     * Deletes a single image that belongs to a product.
     * Removes both the file from disk and the DB record.
     */
    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(@PathVariable Long productId, @PathVariable Long imageId) {
        theProductService.deleteProductImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        theProductService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
