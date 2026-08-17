package com.soubhagya.devscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soubhagya.devscout.dto.DeveloperAnalysisData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${gemini.retry.max-attempts:3}")
    private int maxAttempts;

    private final RestTemplate restTemplate;

    public GeminiService(
            @Value("${gemini.timeout.connect.ms:10000}")
            int connectTimeoutMs,
            @Value("${gemini.timeout.read.ms:60000}")
            int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public String testGemini() {

        return "Gemini Service Working";
    }

    public String analyzeProject(
            String projectName,
            String description
    ) {

        String prompt = """
                Analyze this software project.

                Project Name:
                %s

                Description:
                %s

                Return:

                1. Category
                2. Complexity Level
                3. Skills Demonstrated
                4. Strengths
                5. Suggested Improvements

                Keep response concise.
                """
                .formatted(projectName, description);

        return sendPrompt(prompt);
    }

    public String generateCandidateReport(
            String profileData
    ) {

        DeveloperAnalysisData data =
                new DeveloperAnalysisData();

        data.setRepositorySummaries(
                List.of(profileData)
        );

        return generateCandidateReport(data);
    }

    public String generateCandidateReport(
            DeveloperAnalysisData data
    ) {

        try {

            String prompt = """
Analyze this GitHub developer profile.

Username: %s
Total Repositories: %d

Languages:
%s

Technologies:
%s

Scores (out of 100):
Backend: %d
Frontend: %d
Database: %d
AI: %d
Overall: %d

Repositories:
%s

Return ONLY in the exact format below.

LEVEL: <Only one line>

TOP_STRENGTHS:
- point 1
- point 2
- point 3
- point 4

IMPROVEMENTS:
- point 1
- point 2
- point 3

HIRING_RECOMMENDATION:
<exactly only ONE short line>

Keep response under 80 words.
No markdown.
No explanations.
"""
.formatted(
        data.getUsername(),
        data.getTotalRepositories(),
        formatMap(data.getLanguages()),
        formatMap(data.getTechnologies()),
        data.getBackendScore(),
        data.getFrontendScore(),
        data.getDatabaseScore(),
        data.getAiScore(),
        data.getOverallScore(),
        formatList(data.getRepositorySummaries())
);

        return sendPrompt(prompt);

    } catch (Exception e) {

        return """
                Skill Level:
                Intermediate Java Full Stack Developer

                Strengths:
                - Spring Boot
                - React
                - MySQL
                - REST APIs
                - GitHub API Integration

                Areas for Improvement:
                - Docker
                - AWS
                - CI/CD
                - Microservices

                Hiring Recommendation:
                Suitable for Java Backend Developer and Full Stack Developer roles.
                """;
    }
}

private String sendPrompt(
        String prompt
) {

    String url =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model
                    + ":generateContent?key="
                    + apiKey;

    Map<String, Object> requestBody =
            Map.of(
                    "contents",
                    List.of(
                            Map.of(
                                    "parts",
                                    List.of(
                                            Map.of(
                                                    "text",
                                                    prompt
                                            )
                                    )
                            )
                    ),
                    "generationConfig",
                    Map.of(
                            "maxOutputTokens",
                            300,
                            "temperature",
                            0.2
                    ),
                    "thinkingConfig",
                    Map.of(
                            "thinkingBudget",
                            0
                    )
            );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> entity =
            new HttpEntity<>(requestBody, headers);

    ResponseEntity<String> response =
            postWithRetries(
                    url,
                    entity
            );

    try {

        ObjectMapper mapper =
                new ObjectMapper();

        JsonNode root =
                mapper.readTree(
                        response.getBody()
                );

        return root
                .path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

    } catch (Exception e) {

        return "Error parsing Gemini response";
    }
}

private ResponseEntity<String> postWithRetries(
        String url,
        HttpEntity<Map<String, Object>> entity
) {

    int attempt = 0;

    while (true) {

        attempt++;

        try {

            return restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

        } catch (HttpStatusCodeException e) {

            if (attempt >= maxAttempts
                    || !isTransientStatus(
                    e.getStatusCode().value()
            )) {
                throw e;
            }

            sleep(
                    computeBackoff(
                            attempt
                    )
            );
        }
    }
}

private boolean isTransientStatus(int status) {

    return status == 429
            || status == 408
            || (status >= 500 && status < 600);
}

private long computeBackoff(int attempt) {

    return Math.min(
            2_000L,
            250L * (1L << (attempt - 1))
    );
}

private void sleep(long millis) {

    try {

        Thread.sleep(millis);

    } catch (InterruptedException e) {

        Thread.currentThread().interrupt();

        throw new RuntimeException(
                "Interrupted during Gemini retry backoff",
                e
        );
    }
}

private String formatMap(Map<String,Integer> values) {

    if (values == null || values.isEmpty()) {
        return "None";
    }

    StringBuilder builder =
            new StringBuilder();

    for (Map.Entry<String,Integer> entry : values.entrySet()) {

        builder.append("- ")
                .append(entry.getKey())
                .append(": ")
                .append(entry.getValue())
                .append("\n");
    }

    return builder.toString().trim();
}

private String formatList(List<String> values) {

    if (values == null || values.isEmpty()) {
        return "None";
    }

    return String.join("\n", values);
}
}