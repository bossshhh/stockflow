package com.stockflow.inventory;

import jakarta.validation.constraints.Size;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.common.web.PageResponse;
import com.stockflow.inventory.dto.CatalogItemResponse;

/** Public: anyone may browse the shop without signing in (see SecurityConfig). */
@RestController
@Tag(name = "Catalog", description = "Public shop catalog: active products with an in-stock flag (no login needed)")
@RequestMapping(CatalogController.BASE_PATH)
public class CatalogController {

    static final String BASE_PATH = "/api/v1/catalog";
    private static final int MAX_QUERY_LENGTH = 100;

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Operation(summary = "Browse active products, optionally searching by name. Sort by name or price")
    @GetMapping
    public ResponseEntity<PageResponse<CatalogItemResponse>> browse(
            @RequestParam(required = false) @Size(max = MAX_QUERY_LENGTH) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(catalogService.browse(q, pageable)));
    }

    @Operation(summary = "One active product")
    @GetMapping("/{productId}")
    public ResponseEntity<CatalogItemResponse> get(@PathVariable long productId) {
        return ResponseEntity.ok(catalogService.getItem(productId));
    }
}
