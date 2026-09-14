package com.resume.backend.applications;

import com.resume.backend.auth.JwtService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Java equivalent of application-job.json (recruiter, per-job applicant
// list) and candidate-applications.json ("My Applications").
@RestController
public class ApplicationController {

    private final ApplicationReadRepository applicationReadRepository;

    public ApplicationController(ApplicationReadRepository applicationReadRepository) {
        this.applicationReadRepository = applicationReadRepository;
    }

    @GetMapping("/api/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('RECRUITER')")
    public List<ApplicationRow> byJob(@PathVariable UUID jobId) {
        return applicationReadRepository.byJob(jobId);
    }

    @GetMapping("/api/applications/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<ApplicationRow> mine(@AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        return applicationReadRepository.byApplicant(principal.id());
    }

    // Resolves which resume belongs to the caller's own application for a job,
    // so the frontend can then call GET /api/resumes/{id} directly (Phase 3
    // already scopes that download to the caller). Replaces the
    // get-my-resume-url Edge Function's ownership-resolution role.
    @GetMapping("/api/applications/mine/resume")
    @PreAuthorize("hasRole('CANDIDATE')")
    public Map<String, String> myResumeForJob(
            @RequestParam UUID jobId, @AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        return applicationReadRepository.byApplicant(principal.id()).stream()
                .filter(row -> jobId.equals(row.jobId()))
                .findFirst()
                .map(row -> {
                    if (row.resumeFilePath() == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No resume on file");
                    }
                    return Map.of("resumeStoragePath", row.resumeFilePath());
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No application found for this job"));
    }
}
