package com.amidog.app.admin;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> of(
            List<T> content,
            int page,
            int size,
            long totalElements) {
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact(
                        Math.min(Integer.MAX_VALUE,
                                totalElements / size
                                        + (totalElements % size == 0
                                        ? 0 : 1)));
        return new PageResponse<>(
                content, page, size, totalElements, totalPages);
    }
}
