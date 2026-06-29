package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.FinalReportDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.dto.ProfileAnalysisDTO;
import com.soubhagya.devscout.dto.TechnologyAnalysisDTO;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.soubhagya.devscout.dto.GitHubProfileDTO;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

@Service
public class GitHubService {

        @Value("${github.token}")
private String githubToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<GitHubRepoDTO> getRepositories(String username) {

    String url =
            "https://api.github.com/users/"
                    + username
                    + "/repos";

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

    List<GitHubRepoDTO> repos =
            getRepositories(username);

    Map<String,Integer> languageCount =
            new HashMap<>();

    for (GitHubRepoDTO repo : repos) {

        String language = repo.getLanguage();

        if(language != null) {

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

    List<GitHubRepoDTO> repos =
            getRepositories(username);

    Map<String,Integer> techMap =
            new HashMap<>();

    for(GitHubRepoDTO repo : repos) {

        String description =
                repo.getDescription();

        if(description == null)
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

    if(description.contains(keyword)) {

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

    TechnologyAnalysisDTO techData =
            detectTechnologies(username);

    Map<String,Integer> techs =
            techData.getTechnologies();

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

public FinalReportDTO generateFinalReport(
        String username,
        String aiAnalysis
) {

    DeveloperScoreDTO score =
            calculateScore(username);

    TechnologyAnalysisDTO tech =
            detectTechnologies(username);

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
            tech.getTechnologies()
    );

    report.setAiAnalysis(
            aiAnalysis
    );

    return report;
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
}