package com.example.productservice.exception;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("Product with sku '%s' already exists".formatted(sku));
    }
}
