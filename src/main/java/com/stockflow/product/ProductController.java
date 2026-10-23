package com.stockflow.product;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.common.web.PageResponse;
import com.stockflow.product.dto.CreateProductRequest;
import com.stockflow.product.dto.ProductResponse;
import com.stockflow.product.dto.UpdateProductRequest;

@RestController
@Tag(name = "Products", description = "Product catalog: unique SKUs, prices, activation")
@RequestMapping(ProductController.BASE_PATH)
public class ProductController {

    static final String BASE_PATH = "/api/v1/products";

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Create a product (SKU is stored upper-case and must be unique)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Get one product")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @Operation(summary = "List products, paged. Sort by id, sku, name, price, createdAt or updatedAt")
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(productService.list(active, pageable)));
    }

    @Operation(summary = "Update name, description and price (the SKU never changes)")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable long id,
                                                  @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    /** Deactivates rather than deletes: orders keep referencing the product. */
    @Operation(summary = "Deactivate a product (it stays in order history; it can no longer be ordered)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
