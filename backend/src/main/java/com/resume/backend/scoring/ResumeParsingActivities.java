package com.resume.backend.scoring;

import io.temporal.activity.ActivityInterface;

// Java equivalent of temporal/src/activities/resume_parsing.py.
@ActivityInterface
public interface ResumeParsingActivities {
    String extractResumeText(String resumeStorageId);
}
