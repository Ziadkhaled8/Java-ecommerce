package com.ecommerce.dreamstore.specification;

import com.ecommerce.dreamstore.entity.Product;
import com.ecommerce.dreamstore.dto.request.ProductFilter;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ProductSpecifications {
    public static Specification<Product> filter(ProductFilter f) {
        List<Specification<Product>> specs = Stream.of(
                        nameContains(f.name()),
                        brandContains(f.brand()),
                        hasCategory(f.categoryId()),
                        priceGreaterThanOrEqual(f.minPrice()),
                        priceLessThanOrEqual(f.maxPrice()),
                        isAvailable())
                .filter(Objects::nonNull)
                .toList();

        return specs.isEmpty() ? noFilter() : Specification.allOf(specs);
    }
    public static Specification<Product> nameContains(String name){
        if(name==null ||name.isBlank()) return null;
        String pattern=likePattern(name);
        return (root, _, cb) ->
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
    }
    public static Specification<Product> brandContains(String brand) {
        if (brand == null || brand.isBlank()) return null;
        String pattern = likePattern(brand);
        return (root, _, cb) ->
                cb.like(cb.lower(root.get("brand")), pattern, ESCAPE);
    }

    public static Specification<Product> hasCategory(Long categoryId) {
        if (categoryId == null) return null;
        // Reads the FK column directly; no join for a @ManyToOne id
        return (root, _, cb) ->
                cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> priceGreaterThanOrEqual(BigDecimal minPrice) {
        if (minPrice == null) return null;
        return (root, _, cb) ->
                cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessThanOrEqual(BigDecimal maxPrice) {
        if (maxPrice == null) return null;
        return (root, _, cb) ->
                cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> isAvailable() {
        return (root, _, cb) ->
                cb.greaterThan(root.get("stockQuantity"), 0);
    }
    private static Specification<Product> noFilter() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static final char ESCAPE = '\\';

    /** Lowercases, escapes LIKE wildcards in user input, and wraps in %...% */
    private static String likePattern(String input) {
        String escaped = input.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
