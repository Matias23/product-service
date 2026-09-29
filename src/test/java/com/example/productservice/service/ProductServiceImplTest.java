package com.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.entity.Product;
import com.example.productservice.exception.DuplicateSkuException;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    private ProductServiceImpl productService;

    private final ProductRequest request =
            new ProductRequest("Mouse", "Wireless mouse", new BigDecimal("19.99"), 10, "MS-001");

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, new ProductMapper());
    }

    @Test
    void create_persistsAndReturnsProduct() {
        when(productRepository.existsBySku("MS-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        var response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Mouse");
        assertThat(response.sku()).isEqualTo("MS-001");
    }

    @Test
    void create_throwsWhenSkuExists() {
        when(productRepository.existsBySku("MS-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("MS-001");
        verify(productRepository, never()).save(any());
    }

    @Test
    void findById_returnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));

        var response = productService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.price()).isEqualByComparingTo("19.99");
    }

    @Test
    void findById_throwsWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findAll_returnsMappedPage() {
        var pageable = PageRequest.of(0, 2);
        when(productRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(product(1L), product(2L)), pageable, 5));

        var page = productService.findAll(pageable);

        assertThat(page.content()).extracting("id").containsExactly(1L, 2L);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void update_modifiesExistingProduct() {
        var existing = product(1L);
        var updateRequest = new ProductRequest("Keyboard", null, new BigDecimal("49.90"), 3, "KB-001");
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("KB-001", 1L)).thenReturn(false);
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        var response = productService.update(1L, updateRequest);

        assertThat(response.name()).isEqualTo("Keyboard");
        assertThat(response.description()).isNull();
        assertThat(response.stock()).isEqualTo(3);
        assertThat(response.sku()).isEqualTo("KB-001");
    }

    @Test
    void update_throwsWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void update_throwsWhenSkuTakenByAnotherProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L)));
        when(productRepository.existsBySkuAndIdNot("MS-001", 1L)).thenReturn(true);

        assertThatThrownBy(() -> productService.update(1L, request))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_removesProduct() {
        var existing = product(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.delete(1L);

        verify(productRepository).delete(existing);
    }

    @Test
    void delete_throwsWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).delete(any());
    }

    private static Product product(Long id) {
        var product = new Product();
        product.setId(id);
        product.setName("Mouse");
        product.setDescription("Wireless mouse");
        product.setPrice(new BigDecimal("19.99"));
        product.setStock(10);
        product.setSku("MS-00" + id);
        return product;
    }
}
