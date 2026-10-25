package com.ecommerce.dreamstore.exception;

public class CategoryHasProductsException extends RuntimeException {
    public CategoryHasProductsException(Long categoryId, int productCount) {
        super("Cannot delete category with id " + categoryId +
              ": it still has " + productCount + " attached product(s). " +
              "Reassign or delete the products first.");
    }
}
