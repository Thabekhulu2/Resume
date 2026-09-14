package com.resume.backend.applications;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationRow(
        UUID candidateId, String candidateName, String resumeFilePath, String status,
        UUID jobId, String jobTitle, Double score, OffsetDateTime appliedAt) {
}
