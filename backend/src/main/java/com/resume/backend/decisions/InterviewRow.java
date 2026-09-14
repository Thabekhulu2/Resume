package com.resume.backend.decisions;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InterviewRow(
        UUID id, UUID candidateId, String candidateName, UUID jobId, String jobTitle,
        OffsetDateTime scheduledAt, String status, String notes) {
}
