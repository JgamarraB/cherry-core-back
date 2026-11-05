package com.cherrycore.shared.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * Base de todas las entidades JPA con las columnas comunes:
 * id UUID, auditoría, borrado lógico y bloqueo optimista.
 *
 * <p>Las entidades hijas deben llevar
 * {@code @SQLRestriction(SoftDelete.NOT_DELETED)} para que los registros
 * borrados no aparezcan nunca en las consultas.</p>
 *
 * <p>Implementa {@link Persistable} porque el id lo genera la aplicación:
 * así Spring Data sabe si es un INSERT o un UPDATE sin consultar antes.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Transient
    private boolean isNew = true;

    protected AuditableJpaEntity() {
    }

    protected AuditableJpaEntity(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    /**
     * Borrado lógico: el registro se queda en la base de datos pero deja de verse.
     */
    public void markDeleted(Instant at, UUID by) {
        this.deletedAt = at;
        this.deletedBy = by;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public long getVersion() {
        return version;
    }
}
