package com.cherrycore.shared.application;

/**
 * Página pedida en un listado. Se normaliza para que nadie pueda pedir
 * páginas gigantes (protección contra abusos).
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public long offset() {
        return (long) page * size;
    }
}
