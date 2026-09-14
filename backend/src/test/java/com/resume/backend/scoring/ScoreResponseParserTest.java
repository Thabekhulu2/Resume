package com.resume.backend.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ScoreResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VALID_RESPONSE = """
            {"name": "Jane Doe", "skills": ["Java", "SQL"], "experience": [{"title": "Engineer", "company": "Acme", "duration": "2y", "summary": "Built things"}], "score": 82, "reasoning": "Strong match"}
            """;

    @Test
    void parsesAWellFormedResponse() {
        ScoreResultDto result = ScoreResponseParser.parse(VALID_RESPONSE, objectMapper);

        assertThat(result.getName()).isEqualTo("Jane Doe");
        assertThat(result.getSkills()).containsExactly("Java", "SQL");
        assertThat(result.getExperience()).hasSize(1);
        assertThat(result.getExperience().get(0)).containsEntry("company", "Acme");
        assertThat(result.getScore()).isEqualTo(82.0);
        assertThat(result.getReasoning()).isEqualTo("Strong match");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> ScoreResponseParser.parse("not json at all", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void rejectsNonObjectJson() {
        assertThatThrownBy(() -> ScoreResponseParser.parse("[1,2,3]", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an object");
    }

    @Test
    void rejectsMissingName() {
        String response = """
                {"skills": [], "experience": [], "score": 50, "reasoning": "ok"}
                """;
        assertThatThrownBy(() -> ScoreResponseParser.parse(response, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'name'");
    }

    @Test
    void rejectsNonStringSkillsEntries() {
        String response = """
                {"name": "A", "skills": [1, 2], "experience": [], "score": 50, "reasoning": "ok"}
                """;
        assertThatThrownBy(() -> ScoreResponseParser.parse(response, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'skills'");
    }

    @Test
    void rejectsExperienceEntryMissingARequiredField() {
        String response = """
                {"name": "A", "skills": [], "experience": [{"title": "x", "company": "y", "duration": "z"}], "score": 50, "reasoning": "ok"}
                """;
        assertThatThrownBy(() -> ScoreResponseParser.parse(response, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'experience'");
    }

    @Test
    void rejectsNonNumericScore() {
        String response = """
                {"name": "A", "skills": [], "experience": [], "score": "high", "reasoning": "ok"}
                """;
        assertThatThrownBy(() -> ScoreResponseParser.parse(response, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'score'");
    }

    @Test
    void rejectsBooleanScoreEvenThoughJacksonTreatsItAsAValueNode() {
        String response = """
                {"name": "A", "skills": [], "experience": [], "score": true, "reasoning": "ok"}
                """;
        assertThatThrownBy(() -> ScoreResponseParser.parse(response, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'score'");
    }

    @Test
    void rejectsMissingReasoning() {
        String response = """
                {"name": "A", "skills": [], "experience": [], "score": 50}
                """;
        assertThatThrownBy(() -> ScoreResponseParser.parse(response, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'reasoning'");
    }
}
