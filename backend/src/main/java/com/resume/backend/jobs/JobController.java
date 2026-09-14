package com.resume.backend.jobs;

import com.resume.backend.auth.JwtService;
import com.resume.backend.core.CoreEntityService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Java equivalent of frontend/src/pages/jobs.json, applications.json's job
// picker, and candidate-jobs.json's open-jobs listing. job_description is an
// entity_type in the generic entities/entity_versions model, not its own table.
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobReadRepository jobReadRepository;
    private final CoreEntityService coreEntityService;

    public JobController(JobReadRepository jobReadRepository, CoreEntityService coreEntityService) {
        this.jobReadRepository = jobReadRepository;
        this.coreEntityService = coreEntityService;
    }

    public record CreateJobRequest(String title, String jdText, String location) {
    }

    public record UpdateJobStatusRequest(String status) {
    }

    @GetMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public List<JobSummary> list(@RequestParam(defaultValue = "all") String status) {
        return jobReadRepository.listJobs(status);
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public Map<String, Object> create(@RequestBody CreateJobRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", request.title());
        data.put("jd_text", request.jdText());
        data.put("location", request.location());
        data.put("status", "open");
        var result = coreEntityService.createEntity("job_description", data);
        return Map.of("id", result.entityId().toString());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        try {
            var snapshot = coreEntityService.getEntity(id);
            Map<String, Object> response = new LinkedHashMap<>(snapshot.data());
            response.put("id", snapshot.id().toString());
            return response;
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public Map<String, Object> updateStatus(@PathVariable UUID id, @RequestBody UpdateJobStatusRequest request) {
        try {
            var current = coreEntityService.getEntity(id);
            Map<String, Object> data = new LinkedHashMap<>(current.data());
            data.put("status", request.status());
            var result = coreEntityService.updateEntityScd2(id, data);
            return Map.of("id", result.entityId().toString());
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
    }

    @GetMapping("/open")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<OpenJob> openJobs(@AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        return jobReadRepository.listOpenJobsFor(principal.id());
    }
}
