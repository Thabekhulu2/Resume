package com.resume.backend.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ScoringActivitiesImpl implements ScoringActivities {

    private static final String PROMPT_TEMPLATE = """
            You are evaluating a candidate's resume against a job description.

            Resume:
            %s

            Job Description:
            %s

            Respond with ONLY a JSON object (no markdown, no commentary) matching this exact shape:
            {"name": "string (the candidate's full name as it appears on the resume, or empty string if not found)", "skills": ["string", ...], "experience": [{"title": "string", "company": "string", "duration": "string", "summary": "string"}, ...], "score": <number 0-100>, "reasoning": "string"}
            """;

    private final String anthropicApiKey;
    private final String anthropicModel;
    private final boolean useLocalLlm;
    private final String ollamaBaseUrl;
    private final String ollamaModel;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public ScoringActivitiesImpl(
            @Value("${anthropic.api-key}") String anthropicApiKey,
            @Value("${anthropic.model}") String anthropicModel,
            @Value("${llm.use-local}") boolean useLocalLlm,
            @Value("${llm.ollama.base-url}") String ollamaBaseUrl,
            @Value("${llm.ollama.model}") String ollamaModel,
            ObjectMapper objectMapper) {
        this.anthropicApiKey = anthropicApiKey;
        this.anthropicModel = anthropicModel;
        this.useLocalLlm = useLocalLlm;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScoreResultDto extractAndScore(String resumeText, String jdText) {
        String prompt = PROMPT_TEMPLATE.formatted(resumeText, jdText);
        String rawResponse = useLocalLlm ? callLocalLlm(prompt) : callAnthropic(prompt);
        return ScoreResponseParser.parse(rawResponse, objectMapper);
    }

    private String callAnthropic(String prompt) {
        Map<String, Object> body = Map.of(
                "model", anthropicModel,
                "max_tokens", 2048,
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", "2023-06-01")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("content") instanceof List<?> blocks)) {
            throw new IllegalStateException("Anthropic response missing 'content'");
        }
        StringBuilder text = new StringBuilder();
        for (Object blockObj : blocks) {
            if (blockObj instanceof Map<?, ?> block && "text".equals(block.get("type"))) {
                text.append(block.get("text"));
            }
        }
        return text.toString();
    }

    private String callLocalLlm(String prompt) {
        // TEMPORARY LOCAL TEST PATCH -- mirrors scoring.py's _call_local_llm.
        // Substitutes a local Ollama model so plumbing can be verified without
        // a paid API key. Requires USE_LOCAL_LLM=true; not the production path.
        Map<String, Object> body = Map.of("model", ollamaModel, "prompt", prompt, "stream", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(ollamaBaseUrl + "/api/generate")
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(response.get("response") instanceof String text)) {
            throw new IllegalStateException("Ollama response missing 'response'");
        }
        return text;
    }
}
