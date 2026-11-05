package com.cherrycore.catalog.application;

/**
 * Marca de una app para el login: nombre y logo (o {@code null}).
 */
public record ApplicationBranding(String name, String logoUrl) {
}
