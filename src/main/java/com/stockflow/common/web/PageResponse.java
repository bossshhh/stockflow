package com.stockflow.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for paged results. Spring's {@code Page} is not serialized directly because its JSON
 * layout is an implementation detail that can change between Spring Data versions.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
