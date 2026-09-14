package com.resume.backend.scoring;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Java Temporal SDK equivalent of
// temporal/src/workflows/score_resume_fit/workflow.py's ScoreResumeFitWorkflow.
// Temporal instantiates this via a public no-arg constructor -- it is NOT a
// Spring bean, so activity stubs are created directly rather than injected.
public class ScoreResumeFitWorkflowImpl implements ScoreResumeFitWorkflow {

    private final ResumeParsingActivities resumeParsingActivities = Workflow.newActivityStub(
            ResumeParsingActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(2)).build());

    private final ScoringActivities scoringActivities = Workflow.newActivityStub(
            ScoringActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(3))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    private final CoreEntityActivities coreEntityActivities = Workflow.newActivityStub(
            CoreEntityActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

    @Override
    public ScoreResumeFitResult run(ScoreResumeFitRequest request) {
        String candidateEntityId = request.getCandidateEntityId();
        try {
            String resumeText = resumeParsingActivities.extractResumeText(request.getResumeStoragePath());
            ScoreResultDto score = scoringActivities.extractAndScore(resumeText, request.getJdText());

            Map<String, Object> extracted = new LinkedHashMap<>();
            extracted.put("skills", score.getSkills());
            extracted.put("experience", score.getExperience());

            Map<String, Object> candidateData = new LinkedHashMap<>();
            candidateData.put("name", score.getName());
            candidateData.put("resume_file_path", request.getResumeStoragePath());
            candidateData.put("resume_text", resumeText);
            candidateData.put("extracted", extracted);
            candidateData.put("status", "scored");

            EntityResultDto entityResult;
            if (candidateEntityId != null && !candidateEntityId.isBlank()) {
                entityResult = coreEntityActivities.updateEntityScd2(candidateEntityId, candidateData);
            } else {
                entityResult = coreEntityActivities.createEntity("candidate", candidateData);
                candidateEntityId = entityResult.getEntityId();
            }

            coreEntityActivities.createRelationship(
                    request.getJobDescriptionEntityId(), candidateEntityId, "candidate_scored_against_job", Map.of());

            Map<String, Object> factMetadata = new LinkedHashMap<>();
            factMetadata.put("reasoning", score.getReasoning());
            factMetadata.put("observed_at", Instant.ofEpochMilli(Workflow.currentTimeMillis()).toString());
            String factId = coreEntityActivities.upsertEntityFact(candidateEntityId, "jd_fit_score", score.getScore(), factMetadata);

            ScoreResumeFitResult result = new ScoreResumeFitResult();
            result.setCandidateEntityId(candidateEntityId);
            result.setVersionId(entityResult.getVersionId());
            result.setScore(score.getScore());
            result.setReasoning(score.getReasoning());
            result.setFactId(factId);
            return result;
        } catch (RuntimeException e) {
            // Record a distinct "failed" state (per spec's acceptance criteria)
            // rather than leaving the candidate stuck looking like scoring is
            // still in progress. Activity failures arrive wrapped (ActivityFailure
            // wrapping an ApplicationFailure) -- walk to the innermost cause for
            // a useful message, matching workflow.py's exc.__cause__ walk.
            if (candidateEntityId != null && !candidateEntityId.isBlank()) {
                Throwable rootCause = e;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }

                EntitySnapshotDto current = coreEntityActivities.getEntity(candidateEntityId);
                Map<String, Object> failedData = new LinkedHashMap<>(current.getData() != null ? current.getData() : Map.of());
                failedData.put("status", "failed");
                failedData.put("error", rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.toString());
                coreEntityActivities.updateEntityScd2(candidateEntityId, failedData);
            }
            throw e;
        }
    }
}
