package com.resume.backend.scoring;

import io.temporal.activity.ActivityInterface;

// Java equivalent of temporal/src/activities/scoring.py's extract_and_score.
@ActivityInterface
public interface ScoringActivities {
    ScoreResultDto extractAndScore(String resumeText, String jdText);
}
