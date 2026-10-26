package com.fooddelivery.web.model;

/**
 * Pagination info returned in {@code "meta"} by {@link ApiResponse#page}. Page numbers start at 0.
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    // Computes totalPages from size and totalElements (0 if size is not positive)
    public static PageMeta of(int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageMeta(page, size, totalElements, totalPages);
    }
}
