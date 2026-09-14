package com.resume.backend.decisions;

import com.resume.backend.auth.JwtService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Java equivalent of the record_recruiter_decision RPC and the Scheduled
// Interviews page (candidate-scorecard.json / recruiter-interviews.json).
@RestController
@PreAuthorize("hasRole('RECRUITER')")
public class DecisionController {

    private final DecisionService decisionService;
    private final InterviewReadRepository interviewReadRepository;

    public DecisionController(DecisionService decisionService, InterviewReadRepository interviewReadRepository) {
        this.decisionService = decisionService;
        this.interviewReadRepository = interviewReadRepository;
    }

    public record RecordDecisionRequest(String decision, OffsetDateTime scheduledAt, String notes) {
    }

    @PostMapping("/api/candidates/{id}/decisions")
    public Map<String, String> recordDecision(
            @PathVariable UUID id,
            @RequestBody RecordDecisionRequest request,
            @AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        UUID decisionId = decisionService.recordDecision(
                id, request.decision(), request.scheduledAt(), request.notes(), principal.id());
        return Map.of("id", decisionId.toString());
    }

    @GetMapping("/api/interviews")
    public List<InterviewRow> interviews() {
        return interviewReadRepository.listAll();
    }
}
