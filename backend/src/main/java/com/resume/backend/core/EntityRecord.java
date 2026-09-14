package com.resume.backend.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

// Named EntityRecord, not Entity, to avoid clashing with jakarta.persistence.Entity.
@Entity
@Table(name = "entities")
public class EntityRecord {

    @Id
    private UUID id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    // Set only for candidate entities created via a candidate's own
    // self-service application (spec 0010); recruiter-driven bulk scoring
    // leaves this null. FKs to candidates(id) since migration 20260914090000.
    @Column(name = "applicant_id")
    private UUID applicantId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected EntityRecord() {
    }

    public EntityRecord(UUID id, String entityType) {
        this(id, entityType, null);
    }

    public EntityRecord(UUID id, String entityType, UUID applicantId) {
        this.id = id;
        this.entityType = entityType;
        this.applicantId = applicantId;
    }

    public UUID getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getApplicantId() {
        return applicantId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
