package com.stockflow.inventory;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.common.web.SortValidation;
import com.stockflow.inventory.dto.CatalogItemResponse;
import com.stockflow.product.ProductNotFoundException;

/** The public shop window: active products with an in-stock flag, readable without signing in. */
@Service
public class CatalogService {

    static final Set<String> SORTABLE_FIELDS = Set.of("name", "price");
    private static final Sort DEFAULT_ORDER = Sort.by("name").and(Sort.by("id"));
    private static final char LIKE_ESCAPE = '\\';

    private final InventoryRepository inventoryRepository;

    public CatalogService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * @param query optional case-insensitive substring of the product name; blank means no filter
     */
    @Transactional(readOnly = true)
    public Page<CatalogItemResponse> browse(String query, Pageable pageable) {
        SortValidation.requireAllowed(pageable.getSort(), SORTABLE_FIELDS);
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_ORDER);
        if (query == null || query.isBlank()) {
            return inventoryRepository.findCatalog(effective);
        }
        return inventoryRepository.findCatalogByName(containsPattern(query), effective);
    }

    /** Inactive products are reported as not found: for shoppers they no longer exist. */
    @Transactional(readOnly = true)
    public CatalogItemResponse getItem(long productId) {
        return inventoryRepository.findCatalogItem(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    /**
     * "%term%" with the user's own % and _ escaped, so searching for "50%" finds "50% off" literally
     * instead of treating % as a wildcard. Lower-cased to match the lower(name) in the query.
     */
    static String containsPattern(String query) {
        StringBuilder pattern = new StringBuilder("%");
        for (char c : query.strip().toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                pattern.append(LIKE_ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }
}
