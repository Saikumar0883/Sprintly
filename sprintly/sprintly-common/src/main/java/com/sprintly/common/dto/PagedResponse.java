package com.sprintly.common.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Paginated response wrapper for list endpoints.
 *
 * Design Pattern: Builder
 * Used by task listing, user listing, etc.
 */
@Getter
@Builder
public class PagedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    public static <T> PagedResponse<T> of(List<T> content, int page, int size,
                                           long totalElements, int totalPages) {
        return PagedResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }
}
