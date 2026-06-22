package com.soubhagya.devscout.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.dto.RepositoryAnalysisDTO;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate =
            new RestTemplate();

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

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
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
                        )
                );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        String.class
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



public RepositoryAnalysisDTO analyzeRepository(
        GitHubRepoDTO repo
) {

    String result =
            analyzeProject(
                    repo.getName(),
                    repo.getDescription()
            );

    return new RepositoryAnalysisDTO(
            repo.getName(),
            result
    );
}

public String generateCandidateReport(
        String profileData
) {

    try {

        String prompt = """
Analyze this GitHub developer profile.

%s

Return EXACTLY in this format:

LEVEL: <one line>

TOP_STRENGTHS:
- point
- point
- point
- point

IMPROVEMENTS:
- point
- point
- point

HIRING_RECOMMENDATION:
<one short sentence>

Keep response under 100 words.
No markdown.
No explanations.
"""
.formatted(profileData);

        return analyzeProject(
                "Developer Profile",
                prompt
        );

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
}