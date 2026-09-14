package com.resume.backend.decisions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Java equivalent of the recruiter_scheduled_interviews view.
@Repository
public class InterviewReadRepository {

    private final JdbcTemplate jdbcTemplate;

    public InterviewReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InterviewRow> listAll() {
        String sql = """
                select i.id, i.candidate_id, cv.data ->> 'name' as candidate_name, i.job_id,
                       j_ev.data ->> 'title' as job_title, i.scheduled_at, i.status, i.notes
                from interviews i
                join entity_versions cv on cv.entity_id = i.candidate_id and cv.is_current
                left join entities j on j.id = i.job_id
                left join entity_versions j_ev on j_ev.entity_id = j.id and j_ev.is_current
                order by i.scheduled_at asc
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new InterviewRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("candidate_id"),
                rs.getString("candidate_name"),
                (UUID) rs.getObject("job_id"),
                rs.getString("job_title"),
                rs.getObject("scheduled_at", OffsetDateTime.class),
                rs.getString("status"),
                rs.getString("notes")));
    }
}
