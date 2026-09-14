package com.resume.backend.jobs;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Read-model queries for job_description entities. Kept out of
// CoreEntityService (Phase 2), which only does single-entity CRUD -- listing/
// filtering across entities is a Phase 5 (REST API) concern.
@Repository
public class JobReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public JobReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<JobSummary> listJobs(String statusFilter) {
        String sql = """
                select e.id, ev.data ->> 'title' as title, ev.data ->> 'location' as location,
                       ev.data ->> 'status' as status, ev.data ->> 'jd_text' as jd_text, e.created_at
                from entities e
                join entity_versions ev on ev.entity_id = e.id and ev.is_current
                where e.entity_type = 'job_description'
                  and (? = 'all' or ev.data ->> 'status' = ?)
                order by e.created_at desc
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new JobSummary(
                (UUID) rs.getObject("id"),
                rs.getString("title"),
                rs.getString("location"),
                rs.getString("status"),
                rs.getString("jd_text"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)),
                statusFilter, statusFilter);
    }

    public List<OpenJob> listOpenJobsFor(UUID candidateApplicantId) {
        // Bug found & fixed relative to the original candidate_open_jobs view
        // (20260903150000): it checked ONLY aev.data->>'applied_to_job_id',
        // which ScoreResumeFitWorkflow's updateEntityScd2 clears once scoring
        // finishes (it full-replaces the version data with
        // name/resume_file_path/resume_text/extracted/status only) -- so
        // already_applied silently reverted to false for every scored
        // candidate. This exact root cause was already fixed once, for the
        // sibling recruiter_job_applications view, via a
        // candidate_scored_against_job relationship fallback
        // (20260911120000) -- applying the same fallback here rather than
        // leaving the same class of bug live in a second place.
        String sql = """
                select e.id, ev.data ->> 'title' as title, ev.data ->> 'location' as location,
                       ev.data ->> 'jd_text' as jd_text,
                       exists (
                         select 1 from entities ae
                         join entity_versions aev on aev.entity_id = ae.id and aev.is_current
                         left join lateral (
                           select r.parent_id
                           from relationships_v2 r
                           where r.child_id = ae.id
                             and r.relationship_type = 'candidate_scored_against_job'
                             and r.is_current
                           order by r.created_at desc
                           limit 1
                         ) scored_rel on true
                         where ae.applicant_id = ?
                           and coalesce((aev.data ->> 'applied_to_job_id')::uuid, scored_rel.parent_id) = e.id
                       ) as already_applied
                from entities e
                join entity_versions ev on ev.entity_id = e.id and ev.is_current
                where e.entity_type = 'job_description'
                  and ev.data ->> 'status' = 'open'
                order by e.created_at desc
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new OpenJob(
                (UUID) rs.getObject("id"),
                rs.getString("title"),
                rs.getString("location"),
                rs.getString("jd_text"),
                rs.getBoolean("already_applied")),
                candidateApplicantId);
    }
}
