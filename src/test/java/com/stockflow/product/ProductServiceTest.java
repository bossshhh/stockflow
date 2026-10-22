package com.stockflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.stockflow.common.exception.InvalidRequestParameterException;
import com.stockflow.product.dto.CreateProductRequest;
import com.stockflow.product.dto.ProductResponse;
import com.stockflow.product.dto.UpdateProductRequest;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesInputAndStampsTimes() {
        when(productRepository.existsBySku("ABC-123")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        ProductResponse response = productService.create(
                new CreateProductRequest("abc-123", "  Widget  ", "   ", new BigDecimal("9.5")));

        assertThat(response.sku()).isEqualTo("ABC-123");
        assertThat(response.name()).isEqualTo("Widget");
        assertThat(response.description()).isNull();
        assertThat(response.price()).isEqualByComparingTo("9.50").hasToString("9.50");
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsExistingSkuWithoutInserting() {
        when(productRepository.existsBySku("ABC-123")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(
                new CreateProductRequest("abc-123", "Widget", null, BigDecimal.ONE)))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTranslatesSkuConstraintViolationFromConcurrentInsert() {
        when(productRepository.existsBySku("ABC-123")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(integrityViolation(Product.SKU_UNIQUE_CONSTRAINT));

        assertThatThrownBy(() -> productService.create(
                new CreateProductRequest("ABC-123", "Widget", null, BigDecimal.ONE)))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void createRethrowsUnrelatedIntegrityViolation() {
        when(productRepository.existsBySku("ABC-123")).thenReturn(false);
        DataIntegrityViolationException unrelated = integrityViolation("ck_products_price_non_negative");
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> productService.create(
                new CreateProductRequest("ABC-123", "Widget", null, BigDecimal.ONE)))
                .isSameAs(unrelated);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(42L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void listRejectsUnknownSortField() {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> productService.list(null, pageable))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    void updateChangesDetailsButKeepsSkuAndCreatedAt() {
        Product product = new Product("ABC-123", "Old", "old", BigDecimal.ONE, Instant.parse("2026-01-01T00:00:00Z"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.update(1L,
                new UpdateProductRequest(" New ", "new", new BigDecimal("2.25")));

        assertThat(response.sku()).isEqualTo("ABC-123");
        assertThat(response.name()).isEqualTo("New");
        assertThat(response.price()).isEqualByComparingTo("2.25");
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void deactivateIsIdempotent() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Product product = new Product("ABC-123", "Widget", null, BigDecimal.ONE, created);
        product.deactivate(created);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.deactivate(1L);

        assertThat(product.isActive()).isFalse();
        assertThat(product.getUpdatedAt()).isEqualTo(created);
    }

    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "constraint violated", new SQLException("duplicate"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
