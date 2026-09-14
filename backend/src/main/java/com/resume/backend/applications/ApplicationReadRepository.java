package com.resume.backend.applications;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Java equivalent of the recruiter_job_applications view (as fixed by
// 20260911120000_recruiter_job_applications_job_id_fallback.sql): job_id
// falls back to the candidate_scored_against_job relationship once scoring
// clears applied_to_job_id from the candidate's version data. One query
// serves both "applications for a job" (recruiter) and "my applications"
// (candidate) depending on which of jobId/applicantId is passed.
@Repository
public class ApplicationReadRepository {

    private static final String BASE_QUERY = """
            select c.id as candidate_id, cv.data ->> 'name' as candidate_name,
                   cv.data ->> 'resume_file_path' as resume_file_path, cv.data ->> 'status' as status,
                   coalesce((cv.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id) as job_id,
                   j_ev.data ->> 'title' as job_title,
                   (select value from entity_facts ef where ef.entity_id = c.id order by ef.created_at desc limit 1) as score,
                   c.created_at as applied_at
            from entities c
            join entity_versions cv on cv.entity_id = c.id and cv.is_current
            left join lateral (
              select r.parent_id
              from relationships_v2 r
              where r.child_id = c.id
                and r.relationship_type = 'candidate_scored_against_job'
                and r.is_current
              order by r.created_at desc
              limit 1
            ) scored_rel on true
            left join entities j on j.id = coalesce((cv.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id)
            left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
            where c.entity_type = 'candidate'
              and c.applicant_id is not null
              and (?::uuid is null or coalesce((cv.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id) = ?::uuid)
              and (?::uuid is null or c.applicant_id = ?::uuid)
            order by c.created_at desc
            """;

    private final JdbcTemplate jdbcTemplate;

    public ApplicationReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ApplicationRow> byJob(UUID jobId) {
        return query(jobId, null);
    }

    public List<ApplicationRow> byApplicant(UUID applicantId) {
        return query(null, applicantId);
    }

    private List<ApplicationRow> query(UUID jobId, UUID applicantId) {
        return jdbcTemplate.query(BASE_QUERY, (rs, rowNum) -> {
            // entity_facts.value is `numeric` -- JDBC returns BigDecimal, not
            // Double, so this must go through getBigDecimal (null-safe).
            var score = rs.getBigDecimal("score");
            return new ApplicationRow(
                    (UUID) rs.getObject("candidate_id"),
                    rs.getString("candidate_name"),
                    rs.getString("resume_file_path"),
                    rs.getString("status"),
                    (UUID) rs.getObject("job_id"),
                    rs.getString("job_title"),
                    score != null ? score.doubleValue() : null,
                    rs.getObject("applied_at", OffsetDateTime.class));
        }, jobId, jobId, applicantId, applicantId);
    }
}
