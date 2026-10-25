package com.ecommerce.dreamstore.service.implementations;

import com.ecommerce.dreamstore.entity.Image;
import com.ecommerce.dreamstore.entity.Product;
import com.ecommerce.dreamstore.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;

    @Value("${app.image.upload-dir:uploads/images}")
    private String uploadDir;

    /**
     * Saves a list of {@link MultipartFile} images to disk and persists
     * their metadata as {@link Image} entities linked to the given product.
     *
     * @param files   the uploaded files (may be null or empty)
     * @param product the product entity to associate images with
     * @return the list of persisted {@link Image} entities
     */
    public List<Image> saveImages(List<MultipartFile> files, Product product) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload directory: " + uploadPath, e);
        }

        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> storeFile(file, uploadPath, product))
                .toList();
    }

    private Image storeFile(MultipartFile file, Path uploadPath, Product product) {
        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String storedFilename = UUID.randomUUID() + extension;
        Path targetPath = uploadPath.resolve(storedFilename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + originalFilename, e);
        }

        Image image = new Image();
        image.setFileName(originalFilename);
        image.setPath(targetPath.toString());
        image.setContentType(file.getContentType());
        image.setProduct(product);

        return imageRepository.save(image);
    }

    /**
     * Deletes the physical files from disk and removes their DB records
     * for each of the given image IDs. IDs that do not correspond to an
     * existing image are silently skipped (idempotent).
     *
     * @param imageIds the IDs of the images to delete
     */
    public void deleteImages(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        imageIds.forEach(id -> imageRepository.findById(id).ifPresent(this::deleteImage));
    }

    /**
     * Deletes a single {@link Image}: removes the file from disk (best-effort)
     * then deletes the DB record.
     *
     * @param image the image entity to delete
     */
    public void deleteImage(Image image) {
        if (image.getPath() != null) {
            try {
                Files.deleteIfExists(Paths.get(image.getPath()));
            } catch (IOException e) {
                log.warn("Could not delete image file at path '{}': {}", image.getPath(), e.getMessage());
            }
        }
        imageRepository.delete(image);
    }
}
