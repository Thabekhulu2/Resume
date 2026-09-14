package com.resume.backend.candidates;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CandidateSummary(
        UUID id, String name, String resumeFilePath, String status,
        Double score, String jobTitle, OffsetDateTime createdAt) {
}
