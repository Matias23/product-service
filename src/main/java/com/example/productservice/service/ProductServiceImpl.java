package com.example.productservice.service;

import com.example.productservice.dto.PageResponse;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.entity.Product;
import com.example.productservice.exception.DuplicateSkuException;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

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
        log.info("Product created id={} sku={}", saved.getId(), saved.getSku());
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse findById(Long id) {
        log.debug("Fetching product id={}", id);
        return productMapper.toResponse(getProduct(id));
    }

    @Override
    public PageResponse<ProductResponse> findAll(Pageable pageable) {
        log.debug("Listing products page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
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
        var updated = productRepository.saveAndFlush(product);
        log.info("Product updated id={} sku={}", id, updated.getSku());
        return productMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        productRepository.delete(getProduct(id));
        log.info("Product deleted id={}", id);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
