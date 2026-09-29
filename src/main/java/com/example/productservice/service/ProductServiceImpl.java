package com.example.productservice.service;

import com.example.productservice.dto.PageResponse;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.entity.Product;
import com.example.productservice.exception.DuplicateSkuException;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.repository.ProductRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }
        var saved = productRepository.save(productMapper.toEntity(request));
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(getProduct(id));
    }

    @Override
    public PageResponse<ProductResponse> findAll(Pageable pageable) {
        return PageResponse.from(productRepository.findAll(pageable).map(productMapper::toResponse));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        var product = getProduct(id);
        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new DuplicateSkuException(request.sku());
        }
        productMapper.updateEntity(request, product);
        return productMapper.toResponse(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
