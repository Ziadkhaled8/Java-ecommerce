package com.ecommerce.dreamstore.dto.request;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.multipart.MultipartFile;

public record UpdateProductRequest(
        String name,
        String brand,
        String description,
        @Positive Double price,
        @Min(0) Integer stockQuantity,
        Long categoryId,
        /** New image files to add to the product. */
        List<MultipartFile> images,
        /** IDs of existing images to remove from the product. */
        List<Long> removeImageIds
) {
}
