package com.resume.backend.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Extracted from ScoringActivitiesImpl purely for unit-testability (no HTTP
// involved) -- strict shape validation matching scoring.py's _validate_and_parse.
public final class ScoreResponseParser {

    static final List<String> EXPERIENCE_FIELDS = List.of("title", "company", "duration", "summary");

    private ScoreResponseParser() {
    }

    public static ScoreResultDto parse(String rawResponse, ObjectMapper objectMapper) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawResponse);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("LLM response is not valid JSON: " + e.getMessage(), e);
        }

        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("LLM response JSON must be an object");
        }

        JsonNode nameNode = payload.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            throw new IllegalArgumentException("LLM response 'name' must be a string");
        }

        JsonNode skillsNode = payload.get("skills");
        if (skillsNode == null || !skillsNode.isArray() || !allTextual(skillsNode)) {
            throw new IllegalArgumentException("LLM response 'skills' must be a list of strings");
        }
        List<String> skills = new ArrayList<>();
        skillsNode.forEach(node -> skills.add(node.asText()));

        JsonNode experienceNode = payload.get("experience");
        if (experienceNode == null || !experienceNode.isArray() || !allValidExperienceEntries(experienceNode)) {
            throw new IllegalArgumentException(
                    "LLM response 'experience' must be a list of objects with fields " + EXPERIENCE_FIELDS);
        }
        List<Map<String, Object>> experience = objectMapper.convertValue(experienceNode, new TypeReference<>() {
        });

        JsonNode scoreNode = payload.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            throw new IllegalArgumentException("LLM response 'score' must be a number");
        }

        JsonNode reasoningNode = payload.get("reasoning");
        if (reasoningNode == null || !reasoningNode.isTextual()) {
            throw new IllegalArgumentException("LLM response 'reasoning' must be a string");
        }

        return new ScoreResultDto(nameNode.asText(), skills, experience, scoreNode.asDouble(), reasoningNode.asText());
    }

    private static boolean allTextual(JsonNode arrayNode) {
        for (JsonNode item : arrayNode) {
            if (!item.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private static boolean allValidExperienceEntries(JsonNode arrayNode) {
        for (JsonNode item : arrayNode) {
            if (!item.isObject() || !EXPERIENCE_FIELDS.stream().allMatch(item::has)) {
                return false;
            }
        }
        return true;
    }
}
