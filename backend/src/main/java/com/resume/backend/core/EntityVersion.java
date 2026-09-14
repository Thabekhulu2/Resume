package com.resume.backend.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "entity_versions")
public class EntityVersion {

    @Id
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data;

    // Managed entirely by the entity_versions SCD2 trigger (set_entity_version_validity) -- never set from Java.
    @Column(name = "is_current", insertable = false, updatable = false)
    private boolean current;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EntityVersion() {
    }

    public EntityVersion(UUID id, UUID entityId, int versionNumber, Map<String, Object> data) {
        this.id = id;
        this.entityId = entityId;
        this.versionNumber = versionNumber;
        this.data = data;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public boolean isCurrent() {
        return current;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
