package com.resume.backend.candidates;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CandidateScorecard(
        UUID id,
        String name,
        String status,
        String error,
        List<String> skills,
        List<Map<String, Object>> experience,
        Double score,
        String reasoning,
        UUID jobId,
        String jobTitle,
        String latestDecision) {
}
