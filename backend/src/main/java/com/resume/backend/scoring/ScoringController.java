package com.resume.backend.scoring;

import com.resume.backend.core.CoreEntityService;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Java equivalent of supabase/functions/start-scoring-workflow/index.ts:
// resolves/creates the job_description entity, then triggers
// ScoreResumeFitWorkflow (once per resume) via the Temporal Java client.
@RestController
@RequestMapping("/api/scoring")
public class ScoringController {

    private final CoreEntityService coreEntityService;
    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public ScoringController(
            CoreEntityService coreEntityService,
            WorkflowClient workflowClient,
            @Value("${temporal.task-queue}") String taskQueue) {
        this.coreEntityService = coreEntityService;
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    public record StartScoringRequest(
            String candidateEntityId,
            String resumeStoragePath,
            List<String> resumeStoragePaths,
            String jobDescriptionEntityId,
            String jdText,
            String jobTitle,
            String createdBy,
            String applicantId) {
    }

    private record OneResumeResult(String candidateEntityId, String workflowId) {
    }

    @PostMapping("/start")
    public ResponseEntity<Object> start(@RequestBody StartScoringRequest request) {
        List<String> resumeStoragePaths = (request.resumeStoragePaths() != null && !request.resumeStoragePaths().isEmpty())
                ? request.resumeStoragePaths()
                : (isPresent(request.resumeStoragePath()) ? List.of(request.resumeStoragePath()) : List.of());

        if (resumeStoragePaths.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "resumeStoragePath or resumeStoragePaths is required"));
        }
        if (!isPresent(request.jobDescriptionEntityId()) && !isPresent(request.jdText())) {
            return ResponseEntity.badRequest().body(Map.of("error", "one of jobDescriptionEntityId or jdText is required"));
        }

        String jobDescriptionEntityId;
        String jdText = request.jdText();
        if (isPresent(request.jobDescriptionEntityId())) {
            try {
                var snapshot = coreEntityService.getEntity(UUID.fromString(request.jobDescriptionEntityId()));
                Object jdFromData = snapshot.data().get("jd_text");
                if (jdFromData != null) {
                    jdText = jdFromData.toString();
                }
                jobDescriptionEntityId = request.jobDescriptionEntityId();
            } catch (NoSuchElementException e) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "job_description_entity_id not found: " + request.jobDescriptionEntityId()));
            }
        } else {
            Map<String, Object> jobData = new LinkedHashMap<>();
            jobData.put("title", request.jobTitle());
            jobData.put("jd_text", request.jdText());
            jobDescriptionEntityId = coreEntityService.createEntity("job_description", jobData).entityId().toString();
        }

        boolean isBatch = request.resumeStoragePaths() != null && !request.resumeStoragePaths().isEmpty();

        if (isBatch) {
            // The plural (batch) path does NOT stop at the first failing resume --
            // each resume gets its own try/catch so the rest of the batch still
            // gets scored (spec 0004). The singular path below is unchanged and
            // still fails the whole request on error.
            List<Map<String, String>> candidates = new ArrayList<>();
            List<Map<String, String>> failures = new ArrayList<>();
            for (String path : resumeStoragePaths) {
                try {
                    OneResumeResult result = scoreOneResume(
                            path, jobDescriptionEntityId, jdText, null, request.createdBy(), request.applicantId());
                    candidates.add(Map.of("candidate_entity_id", result.candidateEntityId(), "workflow_id", result.workflowId()));
                } catch (RuntimeException e) {
                    failures.add(Map.of("resume_storage_path", path, "error", String.valueOf(e.getMessage())));
                }
            }
            return ResponseEntity.ok(Map.of(
                    "job_description_entity_id", jobDescriptionEntityId,
                    "candidates", candidates,
                    "failures", failures));
        }

        try {
            OneResumeResult result = scoreOneResume(
                    resumeStoragePaths.get(0), jobDescriptionEntityId, jdText,
                    request.candidateEntityId(), request.createdBy(), request.applicantId());
            return ResponseEntity.ok(Map.of(
                    "candidate_entity_id", result.candidateEntityId(),
                    "job_description_entity_id", jobDescriptionEntityId,
                    "workflow_id", result.workflowId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    // Ensures a candidate entity exists for one resume (rather than only
    // inside the workflow) so the caller gets an id back immediately, then
    // triggers scoring for it -- same shape as the Edge Function's scoreOneResume.
    private OneResumeResult scoreOneResume(
            String resumeStoragePath, String jobDescriptionEntityId, String jdText,
            String candidateEntityIdInput, String createdBy, String applicantId) {
        String candidateEntityId = candidateEntityIdInput;
        if (!isPresent(candidateEntityId)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resume_file_path", resumeStoragePath);
            data.put("status", "scoring");
            if (isPresent(applicantId)) {
                data.put("applied_to_job_id", jobDescriptionEntityId);
            }
            UUID applicantUuid = isPresent(applicantId) ? UUID.fromString(applicantId) : null;
            candidateEntityId = coreEntityService.createEntity("candidate", data, applicantUuid).entityId().toString();
        }

        ScoreResumeFitRequest workflowRequest = new ScoreResumeFitRequest();
        workflowRequest.setResumeStoragePath(resumeStoragePath);
        workflowRequest.setJobDescriptionEntityId(jobDescriptionEntityId);
        workflowRequest.setJdText(jdText);
        workflowRequest.setCandidateEntityId(candidateEntityId);
        workflowRequest.setCreatedBy(createdBy);

        String workflowId = "score-resume-fit-" + UUID.randomUUID();
        ScoreResumeFitWorkflow workflow = workflowClient.newWorkflowStub(
                ScoreResumeFitWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId(workflowId).build());
        WorkflowClient.start(workflow::run, workflowRequest);

        return new OneResumeResult(candidateEntityId, workflowId);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
