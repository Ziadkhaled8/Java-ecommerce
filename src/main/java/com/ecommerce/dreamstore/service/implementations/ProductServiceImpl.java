package com.ecommerce.dreamstore.service.implementations;

import com.ecommerce.dreamstore.dto.request.CreateProductRequest;
import com.ecommerce.dreamstore.dto.request.ProductFilter;
import com.ecommerce.dreamstore.dto.request.UpdateProductRequest;
import com.ecommerce.dreamstore.dto.response.ProductResponse;
import com.ecommerce.dreamstore.entity.Category;
import com.ecommerce.dreamstore.entity.Image;
import com.ecommerce.dreamstore.entity.Product;
import com.ecommerce.dreamstore.exception.NotFoundException;
import com.ecommerce.dreamstore.mapper.ProductMapper;
import com.ecommerce.dreamstore.repository.CategoryRepository;
import com.ecommerce.dreamstore.repository.ImageRepository;
import com.ecommerce.dreamstore.repository.ProductRepository;
import com.ecommerce.dreamstore.service.interfaces.ProductService;
import com.ecommerce.dreamstore.specification.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ImageRepository imageRepository;
    private final ImageService imageService;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(ProductFilter filter, Pageable pageable) {
        Specification<Product> spec = ProductSpecifications.filter(filter);
        return productRepository.findAll(spec, pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category not found with id: " + request.categoryId()));

        Product product = productMapper.toEntity(request, category);
        product = productRepository.save(product);

        List<Image> images = imageService.saveImages(request.images(), product);
        product.setImages(images);

        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProductById(id);

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found with id: " + request.categoryId()));
        }

        productMapper.applyUpdate(request, product, category);


        if (request.removeImageIds() != null && !request.removeImageIds().isEmpty()) {
            product.getImages().removeIf(img -> request.removeImageIds().contains(img.getId()));
        }

        imageService.deleteImages(request.removeImageIds());
        product = productRepository.save(product);
        List<Image> newImages = imageService.saveImages(request.images(), product);
        if (!newImages.isEmpty()) {
            product.getImages().addAll(newImages);
        }

        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        Product product = findProductById(productId);
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Image not found with id: " + imageId));

        if (!image.getProduct().getId().equals(product.getId())) {
            throw new NotFoundException("Image " + imageId + " does not belong to product " + productId);
        }

        imageService.deleteImage(image);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        findProductById(id); // ensure it exists before deletion
        productRepository.deleteById(id);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found with id: " + id));
    }
}
