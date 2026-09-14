package com.resume.backend.jobs;

import java.util.UUID;

public record OpenJob(UUID id, String title, String location, String jdText, boolean alreadyApplied) {
}
