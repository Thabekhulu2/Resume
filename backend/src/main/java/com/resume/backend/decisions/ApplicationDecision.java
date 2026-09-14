package com.resume.backend.decisions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "application_decisions")
public class ApplicationDecision {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private String decision;

    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ApplicationDecision() {
    }

    public ApplicationDecision(UUID id, UUID candidateId, String decision, String notes, UUID createdBy) {
        this.id = id;
        this.candidateId = candidateId;
        this.decision = decision;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getDecision() {
        return decision;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
