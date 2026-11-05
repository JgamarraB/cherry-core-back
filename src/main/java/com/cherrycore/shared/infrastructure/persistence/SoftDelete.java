package com.cherrycore.shared.infrastructure.persistence;

/**
 * Condición SQL que excluye los registros con borrado lógico.
 */
public final class SoftDelete {

    public static final String NOT_DELETED = "deleted_at IS NULL";

    private SoftDelete() {
    }
}
