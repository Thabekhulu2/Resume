package com.resume.backend.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Written via a native upsert (CoreEntityService.upsertEntityFact) rather than
// through this entity directly -- JPA has no clean ON CONFLICT support. Mapped
// here so the persisted row can still be read back through normal JPA queries.
@Entity
@Table(name = "entity_facts")
public class EntityFact {

    @Id
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "fact_type_id", nullable = false)
    private UUID factTypeId;

    @Column(nullable = false)
    private BigDecimal value;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    protected EntityFact() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getFactTypeId() {
        return factTypeId;
    }

    public BigDecimal getValue() {
        return value;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
