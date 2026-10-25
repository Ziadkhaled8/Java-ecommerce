package com.ecommerce.dreamstore.mapper;

import com.ecommerce.dreamstore.dto.request.CreateProductRequest;
import com.ecommerce.dreamstore.dto.request.UpdateProductRequest;
import com.ecommerce.dreamstore.dto.response.ImageResponse;
import com.ecommerce.dreamstore.dto.response.ProductResponse;
import com.ecommerce.dreamstore.entity.Category;
import com.ecommerce.dreamstore.entity.Image;
import com.ecommerce.dreamstore.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class ProductMapper {

    /**
     * Maps a {@link CreateProductRequest} to a new {@link Product} entity.
     * Images are NOT set here — they are handled by ImageService.
     */
    public Product toEntity(CreateProductRequest request, Category category) {
        Product product = new Product();
        product.setName(request.name());
        product.setBrand(request.brand());
        product.setDescription(request.description());
        product.setPrice(BigDecimal.valueOf(request.price()));
        product.setStockQuantity(request.stockQuantity());
        product.setCategory(category);
        return product;
    }

    /**
     * Applies non-null fields from an {@link UpdateProductRequest} onto an
     * existing {@link Product} entity (partial / patch semantics).
     * Category is only updated when a new category is provided.
     */
    public void applyUpdate(UpdateProductRequest request, Product product, Category category) {
        if (request.name() != null)          product.setName(request.name());
        if (request.brand() != null)         product.setBrand(request.brand());
        if (request.description() != null)   product.setDescription(request.description());
        if (request.price() != null)         product.setPrice(BigDecimal.valueOf(request.price()));
        if (request.stockQuantity() != null) product.setStockQuantity(request.stockQuantity());
        if (category != null)                product.setCategory(category);
    }

    /**
     * Maps a {@link Product} entity to a {@link ProductResponse} DTO.
     */
    public ProductResponse toResponse(Product product) {
        List<ImageResponse> imageResponses = product.getImages() == null
                ? Collections.emptyList()
                : product.getImages().stream()
                        .map(this::toImageResponse)
                        .toList();

        String categoryName = product.getCategory() != null
                ? product.getCategory().getName()
                : null;

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                categoryName,
                imageResponses
        );
    }

    public ImageResponse toImageResponse(Image image) {
        return new ImageResponse(
                image.getId(),
                image.getFileName(),
                image.getPath(),
                image.getContentType()
        );
    }
}
