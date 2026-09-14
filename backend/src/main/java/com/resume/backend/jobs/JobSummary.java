package com.resume.backend.jobs;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobSummary(UUID id, String title, String location, String status, String jdText, OffsetDateTime createdAt) {
}
