package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperAnalysisData;
import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.FinalReportDTO;
import com.soubhagya.devscout.dto.GitHubProfileDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.dto.ProfileAnalysisDTO;
import com.soubhagya.devscout.dto.RepositoryAnalysisDTO;
import com.soubhagya.devscout.dto.TechnologyAnalysisDTO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubService {

    @Value("${github.token}")
    private String githubToken;

    @Value("${devscout.cache.ttl.minutes:30}")
    private long cacheTtlMinutes;

    private final RestTemplate restTemplate = new RestTemplate();

    private final GeminiService geminiService;

    private final Map<String, CachedReport> reportCache = new ConcurrentHashMap<>();

    public GitHubService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public List<GitHubRepoDTO> getRepositories(String username) {

        String url =
                "https://api.github.com/users/"
                        + username
                        + "/repos?per_page=100";

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(githubToken);

        HttpEntity<String> entity =
                new HttpEntity<>(headers);

        ResponseEntity<List<GitHubRepoDTO>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<List<GitHubRepoDTO>>() {}
                );

        return response.getBody();
    }

    public ProfileAnalysisDTO analyzeProfile(String username) {
        return analyzeProfile(getRepositories(username));
    }

    ProfileAnalysisDTO analyzeProfile(List<GitHubRepoDTO> repos) {

        Map<String,Integer> languageCount =
                new HashMap<>();

        for (GitHubRepoDTO repo : repos) {

            String language = repo.getLanguage();

            if (language != null) {

                languageCount.put(
                        language,
                        languageCount.getOrDefault(
                                language,
                                0
                        ) + 1
                );
            }
        }

        ProfileAnalysisDTO analysis =
                new ProfileAnalysisDTO();

        analysis.setTotalRepositories(
                repos.size()
        );

        analysis.setLanguages(
                languageCount
        );

        return analysis;
    }

    public TechnologyAnalysisDTO detectTechnologies(
            String username
    ) {
        return detectTechnologies(getRepositories(username));
    }

    TechnologyAnalysisDTO detectTechnologies(List<GitHubRepoDTO> repos) {

        Map<String,Integer> techMap =
                new HashMap<>();

        for (GitHubRepoDTO repo : repos) {

            String description =
                    repo.getDescription();

            if (description == null)
                continue;

            description =
                    description.toLowerCase();

            detect(description,
                    "spring boot",
                    "Spring Boot",
                    techMap);

            detect(description,
                    "react",
                    "React",
                    techMap);

            detect(description,
                    "mysql",
                    "MySQL",
                    techMap);

            detect(description,
                    "jwt",
                    "JWT",
                    techMap);

            detect(description,
                    "razorpay",
                    "Razorpay",
                    techMap);

            detect(description,
                    "gemini",
                    "Gemini API",
                    techMap);

            detect(description,
                    "jdbc",
                    "JDBC",
                    techMap);
        }

        TechnologyAnalysisDTO dto =
                new TechnologyAnalysisDTO();

        dto.setTechnologies(techMap);

        return dto;
    }

    private void detect(
            String description,
            String keyword,
            String technology,
            Map<String,Integer> techMap
    ) {

        if (description.contains(keyword)) {

            techMap.put(
                    technology,
                    techMap.getOrDefault(
                            technology,
                            0
                    ) + 1
            );
        }
    }

    public DeveloperScoreDTO calculateScore(
            String username
    ) {
        return calculateScore(
                detectTechnologies(username)
                        .getTechnologies()
        );
    }

    DeveloperScoreDTO calculateScore(Map<String,Integer> techs) {

        int backend = 20;
        int frontend = 20;
        int database = 20;
        int ai = 20;

        backend +=
                techs.getOrDefault(
                        "Spring Boot",
                        0
                ) * 10;

        backend +=
                techs.getOrDefault(
                        "JWT",
                        0
                ) * 5;

        backend +=
                techs.getOrDefault(
                        "JDBC",
                        0
                ) * 5;

        frontend +=
                techs.getOrDefault(
                        "React",
                        0
                ) * 15;

        database +=
                techs.getOrDefault(
                        "MySQL",
                        0
                ) * 10;

        ai +=
                techs.getOrDefault(
                        "Gemini API",
                        0
                ) * 15;

        backend =
                Math.min(
                        backend,
                        100
                );

        frontend =
                Math.min(
                        frontend,
                        100
                );

        database =
                Math.min(
                        database,
                        100
                );

        ai =
                Math.min(
                        ai,
                        100
                );

        int overall =
                (backend +
                        frontend +
                        database +
                        ai) / 4;

        DeveloperScoreDTO dto =
                new DeveloperScoreDTO();

        dto.setBackendScore(
                backend
        );

        dto.setFrontendScore(
                frontend
        );

        dto.setDatabaseScore(
                database
        );

        dto.setAiScore(
                ai
        );

        dto.setOverallScore(
                overall
        );

        return dto;
    }

    public List<RepositoryAnalysisDTO> analyzeRepositories(
            String username
    ) {

        List<GitHubRepoDTO> repos =
                getRepositories(username);

        List<RepositoryAnalysisDTO> result =
                new ArrayList<>();

        for (GitHubRepoDTO repo : repos) {

            String description =
                    repo.getDescription();

            if (description == null)
                continue;

            TechnologyAnalysisDTO local =
                    detectTechnologies(
                            List.of(repo)
                    );

            String analysis =
                    local.getTechnologies().isEmpty()
                            ? "Technologies detected: None"
                            : "Technologies detected: "
                                    + String.join(
                                            ", ",
                                            local.getTechnologies().keySet()
                                    );

            result.add(
                    new RepositoryAnalysisDTO(
                            repo.getName(),
                            analysis
                    )
            );
        }

        return result;
    }

    public FinalReportDTO generateFinalReport(
            String username
    ) {

        CachedReport cached =
                reportCache.get(username);

        if (cached != null && !cached.isExpired()) {
            return cached.report;
        }

        List<GitHubRepoDTO> repos =
                getRepositories(username);

        Map<String,Integer> languages =
                analyzeProfile(repos)
                        .getLanguages();

        Map<String,Integer> technologies =
                detectTechnologies(repos)
                        .getTechnologies();

        DeveloperScoreDTO score =
                calculateScore(technologies);

        DeveloperAnalysisData data =
                new DeveloperAnalysisData();

        data.setUsername(username);

        data.setTotalRepositories(
                repos.size()
        );

        data.setLanguages(
                languages
        );

        data.setTechnologies(
                technologies
        );

        data.setBackendScore(
                score.getBackendScore()
        );

        data.setFrontendScore(
                score.getFrontendScore()
        );

        data.setDatabaseScore(
                score.getDatabaseScore()
        );

        data.setAiScore(
                score.getAiScore()
        );

        data.setOverallScore(
                score.getOverallScore()
        );

        data.setRepositorySummaries(
                buildRepositorySummaries(repos)
        );

        String aiAnalysis =
                geminiService
                        .generateCandidateReport(
                                data
                        );

        FinalReportDTO report =
                new FinalReportDTO();

        report.setUsername(username);

        report.setOverallScore(
                score.getOverallScore()
        );

        report.setBackendScore(
                score.getBackendScore()
        );

        report.setFrontendScore(
                score.getFrontendScore()
        );

        report.setDatabaseScore(
                score.getDatabaseScore()
        );

        report.setAiScore(
                score.getAiScore()
        );

        report.setTechnologies(
                technologies
        );

        report.setAiAnalysis(
                aiAnalysis
        );

        reportCache.put(
                username,
                new CachedReport(
                        report,
                        System.currentTimeMillis()
                                + cacheTtlMinutes * 60_000
                )
        );

        return report;
    }

    private List<String> buildRepositorySummaries(
            List<GitHubRepoDTO> repos
    ) {

        List<String> summaries =
                new ArrayList<>();

        for (GitHubRepoDTO repo : repos) {

            summaries.add(
                    repo.getName()
                            + " - "
                            + (repo.getDescription() == null
                            ? "No description"
                            : repo.getDescription())
            );
        }

        return summaries;
    }

    public GitHubProfileDTO getProfile(
            String username
    ) {

        ProfileAnalysisDTO analysis =
                analyzeProfile(username);

        GitHubProfileDTO dto =
                new GitHubProfileDTO();

        dto.setUsername(username);

        dto.setTotalRepositories(
                analysis.getTotalRepositories()
        );

        String primaryLanguage =
                analysis.getLanguages()
                        .entrySet()
                        .stream()
                        .max(
                                Map.Entry.comparingByValue()
                        )
                        .map(
                                Map.Entry::getKey
                        )
                        .orElse("Unknown");

        dto.setPrimaryLanguage(
                primaryLanguage
        );

        return dto;
    }

    private static class CachedReport {

        private final FinalReportDTO report;

        private final long expiresAt;

        CachedReport(
                FinalReportDTO report,
                long expiresAt
        ) {
            this.report = report;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}