package com.ecommerce.dreamstore.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record CreateProductRequest(@NotBlank String name, @NotBlank String brand,
                                   String description, @NotNull double price,
                                   @NotNull int stockQuantity, @NotNull Long categoryId,
                                   List<MultipartFile> images) {
}

