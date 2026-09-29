package com.example.productservice.service;

import com.example.productservice.dto.PageResponse;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse findById(Long id);

    PageResponse<ProductResponse> findAll(Pageable pageable);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);
}
