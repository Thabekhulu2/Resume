package com.resume.backend.candidates;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Java equivalent of candidate-history.json's embedded query and the
// Dashboard's score-band tiles (fffecb9). Kept out of CoreEntityService
// (Phase 2), which only does single-entity CRUD.
@Repository
public class CandidateReadRepository {

    private static final String SUMMARY_QUERY = """
            select c.id, cv.data ->> 'name' as name, cv.data ->> 'resume_file_path' as resume_file_path,
                   cv.data ->> 'status' as status,
                   (select value from entity_facts ef where ef.entity_id = c.id order by ef.created_at desc limit 1) as score,
                   (select j_ev.data ->> 'title'
                      from relationships_v2 r
                      join entity_versions j_ev on j_ev.entity_id = r.parent_id and j_ev.is_current
                      where r.child_id = c.id and r.relationship_type = 'candidate_scored_against_job' and r.is_current
                      order by r.created_at desc limit 1) as job_title,
                   c.created_at
            from entities c
            join entity_versions cv on cv.entity_id = c.id and cv.is_current
            where c.entity_type = 'candidate'
            """;

    private final JdbcTemplate jdbcTemplate;

    public CandidateReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CandidateSummary> listAll() {
        String sql = SUMMARY_QUERY + " order by c.created_at desc";
        return jdbcTemplate.query(sql, CandidateReadRepository::mapSummary);
    }

    public List<CandidateSummary> listByScoreRange(double min, double max) {
        String sql = "select * from (" + SUMMARY_QUERY + ") ranked where score between ? and ? order by created_at desc";
        return jdbcTemplate.query(sql, CandidateReadRepository::mapSummary, min, max);
    }

    public ScoreBandCounts scoreBandCounts() {
        String sql = """
                select
                  count(*) filter (where ef.value between 0 and 49) as weak,
                  count(*) filter (where ef.value between 50 and 69) as potential,
                  count(*) filter (where ef.value between 70 and 100) as strong
                from entities c
                join entity_facts ef on ef.entity_id = c.id
                join fact_types ft on ft.id = ef.fact_type_id and ft.key = 'jd_fit_score'
                where c.entity_type = 'candidate'
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ScoreBandCounts(
                rs.getLong("weak"), rs.getLong("potential"), rs.getLong("strong")));
    }

    public Optional<FactRow> latestFact(UUID candidateId) {
        String sql = """
                select value, metadata ->> 'reasoning' as reasoning
                from entity_facts
                where entity_id = ?
                order by created_at desc
                limit 1
                """;
        List<FactRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new FactRow(
                rs.getBigDecimal("value"), rs.getString("reasoning")), candidateId);
        return rows.stream().findFirst();
    }

    public Optional<UUID> scoredAgainstJobId(UUID candidateId) {
        String sql = """
                select parent_id
                from relationships_v2
                where child_id = ? and relationship_type = 'candidate_scored_against_job' and is_current
                order by created_at desc
                limit 1
                """;
        List<UUID> rows = jdbcTemplate.query(sql, (rs, rowNum) -> (UUID) rs.getObject("parent_id"), candidateId);
        return rows.stream().findFirst();
    }

    public record FactRow(BigDecimal value, String reasoning) {
    }

    private static CandidateSummary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        // entity_facts.value is `numeric` -- JDBC returns BigDecimal, not
        // Double, so this must go through getBigDecimal (null-safe), never a
        // raw getObject()-then-cast to Double.
        BigDecimal score = rs.getBigDecimal("score");
        return new CandidateSummary(
                (UUID) rs.getObject("id"),
                rs.getString("name"),
                rs.getString("resume_file_path"),
                rs.getString("status"),
                score != null ? score.doubleValue() : null,
                rs.getString("job_title"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
