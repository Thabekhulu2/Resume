package com.resume.backend.decisions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "interviews")
public class Interview {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(insertable = false, updatable = false)
    private String status;

    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    protected Interview() {
    }

    public Interview(UUID id, UUID candidateId, UUID jobId, OffsetDateTime scheduledAt, String notes, UUID createdBy) {
        this.id = id;
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.scheduledAt = scheduledAt;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
