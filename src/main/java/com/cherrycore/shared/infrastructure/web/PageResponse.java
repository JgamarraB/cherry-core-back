package com.cherrycore.shared.infrastructure.web;

import java.util.List;

import com.cherrycore.shared.application.PageResult;

/**
 * Formato común de todos los listados paginados:
 * {@code { items, page, size, totalItems, totalPages }}. Las páginas empiezan en 0.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.items(), result.page(), result.size(), result.totalItems(),
                result.totalPages());
    }
}
