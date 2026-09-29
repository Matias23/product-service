package com.example.productservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    @Test
    void toEntity_copiesAllRequestFields() {
        var request = new ProductRequest("Mouse", "Wireless", new BigDecimal("19.99"), 10, "MS-001");

        var entity = mapper.toEntity(request);

        assertThat(entity.getId()).isNull();
        assertThat(entity)
                .extracting(Product::getName, Product::getDescription, Product::getPrice,
                        Product::getStock, Product::getSku)
                .containsExactly("Mouse", "Wireless", new BigDecimal("19.99"), 10, "MS-001");
    }

    @Test
    void toResponse_copiesAllEntityFields() {
        var now = Instant.now();
        var entity = new Product();
        entity.setId(7L);
        entity.setName("Mouse");
        entity.setDescription("Wireless");
        entity.setPrice(new BigDecimal("19.99"));
        entity.setStock(10);
        entity.setSku("MS-001");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        var response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Mouse");
        assertThat(response.description()).isEqualTo("Wireless");
        assertThat(response.price()).isEqualByComparingTo("19.99");
        assertThat(response.stock()).isEqualTo(10);
        assertThat(response.sku()).isEqualTo("MS-001");
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);
    }

    @Test
    void updateEntity_overwritesMutableFieldsOnly() {
        var entity = new Product();
        entity.setId(7L);
        entity.setName("Old");

        mapper.updateEntity(new ProductRequest("New", null, BigDecimal.ONE, 1, "NEW-1"), entity);

        assertThat(entity.getId()).isEqualTo(7L);
        assertThat(entity.getName()).isEqualTo("New");
        assertThat(entity.getSku()).isEqualTo("NEW-1");
    }
}
