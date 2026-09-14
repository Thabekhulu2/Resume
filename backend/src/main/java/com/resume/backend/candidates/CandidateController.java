package com.resume.backend.candidates;

import com.resume.backend.core.CoreEntityService;
import com.resume.backend.decisions.ApplicationDecisionRepository;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Java equivalent of candidate-history.json, candidate-scorecard.json (read
// side), candidate-range.json, and the Dashboard's score-band tiles.
@RestController
@PreAuthorize("hasRole('RECRUITER')")
public class CandidateController {

    private final CandidateReadRepository candidateReadRepository;
    private final CoreEntityService coreEntityService;
    private final ApplicationDecisionRepository decisionRepository;

    public CandidateController(
            CandidateReadRepository candidateReadRepository,
            CoreEntityService coreEntityService,
            ApplicationDecisionRepository decisionRepository) {
        this.candidateReadRepository = candidateReadRepository;
        this.coreEntityService = coreEntityService;
        this.decisionRepository = decisionRepository;
    }

    public record DeleteCandidatesRequest(List<UUID> ids) {
    }

    @GetMapping("/api/candidates")
    public List<CandidateSummary> list() {
        return candidateReadRepository.listAll();
    }

    @GetMapping("/api/candidates/range")
    public List<CandidateSummary> range(@RequestParam double min, @RequestParam double max) {
        return candidateReadRepository.listByScoreRange(min, max);
    }

    @GetMapping("/api/dashboard/score-bands")
    public ScoreBandCounts scoreBands() {
        return candidateReadRepository.scoreBandCounts();
    }

    @GetMapping("/api/candidates/{id}")
    public CandidateScorecard scorecard(@PathVariable UUID id) {
        var snapshot = fetchOrNotFound(id);
        var fact = candidateReadRepository.latestFact(id);
        var jobId = candidateReadRepository.scoredAgainstJobId(id);
        String jobTitle = jobId.map(this::jobTitleOrNull).orElse(null);
        String latestDecision = decisionRepository.findByCandidateIdOrderByCreatedAtDesc(id).stream()
                .findFirst()
                .map(d -> d.getDecision())
                .orElse(null);

        Map<String, Object> data = snapshot.data();
        Map<String, Object> extracted = data.get("extracted") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        return new CandidateScorecard(
                id,
                (String) data.get("name"),
                (String) data.get("status"),
                (String) data.get("error"),
                (List<String>) extracted.getOrDefault("skills", List.of()),
                (List<Map<String, Object>>) extracted.getOrDefault("experience", List.of()),
                fact.map(f -> f.value().doubleValue()).orElse(null),
                fact.map(CandidateReadRepository.FactRow::reasoning).orElse(null),
                jobId.orElse(null),
                jobTitle,
                latestDecision);
    }

    @DeleteMapping("/api/candidates/{id}")
    public void delete(@PathVariable UUID id) {
        coreEntityService.deleteEntity(id);
    }

    @DeleteMapping("/api/candidates")
    public void deleteBulk(@RequestBody DeleteCandidatesRequest request) {
        request.ids().forEach(coreEntityService::deleteEntity);
    }

    private com.resume.backend.core.EntitySnapshot fetchOrNotFound(UUID id) {
        try {
            return coreEntityService.getEntity(id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found");
        }
    }

    private String jobTitleOrNull(UUID jobId) {
        try {
            Object title = coreEntityService.getEntity(jobId).data().get("title");
            return title != null ? title.toString() : null;
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
