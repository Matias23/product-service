package com.example.productservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.productservice.config.SecurityConfig;
import com.example.productservice.dto.PageResponse;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductResponse;
import com.example.productservice.entity.Product;
import com.example.productservice.exception.DuplicateSkuException;
import com.example.productservice.exception.ProductNotFoundException;
import com.example.productservice.security.ProblemDetailAccessDeniedHandler;
import com.example.productservice.security.ProblemDetailAuthenticationEntryPoint;
import com.example.productservice.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
class ProductControllerTest {

    private static final String BASE_URL = "/api/v1/products";
    private static final String VALID_BODY = """
            {"name":"Mouse","description":"Wireless","price":19.99,"stock":10,"sku":"MS-001"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void create_returns201WithLocation() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(response(1L));

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/products/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("MS-001"));
    }

    @Test
    void create_returns400OnInvalidBody() throws Exception {
        var body = """
                {"name":"","price":-1,"stock":-5}
                """;

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.stock").exists())
                .andExpect(jsonPath("$.errors.sku").exists());
        verifyNoInteractions(productService);
    }

    @Test
    void create_returns409OnDuplicateSku() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenThrow(new DuplicateSkuException("MS-001"));

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Duplicate SKU"));
    }

    @Test
    void findById_returns200() throws Exception {
        when(productService.findById(1L)).thenReturn(response(1L));

        mockMvc.perform(get(BASE_URL + "/1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mouse"));
    }

    @Test
    void findById_returns404WhenMissing() throws Exception {
        when(productService.findById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get(BASE_URL + "/99").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product with id 99 not found"));
    }

    @Test
    void findAll_returnsPage() throws Exception {
        when(productService.findAll(any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(response(1L)), 0, 20, 1, 1));

        mockMvc.perform(get(BASE_URL).with(admin()).param("page", "0").param("size", "20").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void update_returns200() throws Exception {
        when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(response(1L));

        mockMvc.perform(put(BASE_URL + "/1").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        when(productService.update(eq(99L), any(ProductRequest.class)))
                .thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(put(BASE_URL + "/99").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/1").with(admin()))
                .andExpect(status().isNoContent());
        verify(productService).delete(1L);
    }

    @Test
    void delete_returns404WhenMissing() throws Exception {
        doThrow(new ProductNotFoundException(99L)).when(productService).delete(99L);

        mockMvc.perform(delete(BASE_URL + "/99").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unexpectedError_returns500WithoutLeakingDetails() throws Exception {
        when(productService.findById(1L)).thenThrow(new IllegalStateException("db password=secret"));

        mockMvc.perform(get(BASE_URL + "/1").with(admin()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void findAll_withUnknownSortProperty_returns400() throws Exception {
        when(productService.findAll(any(Pageable.class)))
                .thenThrow(new PropertyReferenceException("doesNotExist", TypeInformation.of(Product.class), List.of()));

        mockMvc.perform(get(BASE_URL).with(admin()).param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"))
                .andExpect(jsonPath("$.detail").value("Unknown property 'doesNotExist'"));
    }

    @Test
    void malformedJson_returns400NotServerError() throws Exception {
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    @Test
    void request_withoutToken_returns401ProblemDetail() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        verifyNoInteractions(productService);
    }

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void user_canReadProducts() throws Exception {
        when(productService.findById(1L)).thenReturn(response(1L));

        mockMvc.perform(get(BASE_URL + "/1").with(user()))
                .andExpect(status().isOk());
    }

    @Test
    void user_cannotCreate_returns403ProblemDetail() throws Exception {
        mockMvc.perform(post(BASE_URL).with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verifyNoInteractions(productService);
    }

    @Test
    void user_cannotUpdate_returns403() throws Exception {
        mockMvc.perform(put(BASE_URL + "/1").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    void user_cannotDelete_returns403() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/1").with(user()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("preferred_username", "admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.claim("preferred_username", "alice"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static ProductResponse response(Long id) {
        var now = Instant.now();
        return new ProductResponse(id, "Mouse", "Wireless", new BigDecimal("19.99"), 10, "MS-001", now, now);
    }
}
