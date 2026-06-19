package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
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

@Service
public class GitHubService {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<GitHubRepoDTO> getRepositories(String username) {

        String url =
                "https://api.github.com/users/"
                        + username
                        + "/repos";

        ResponseEntity<List<GitHubRepoDTO>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
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

    var techs =
            techData.getTechnologies();

    int backend = 0;
    int frontend = 0;
    int database = 0;
    int ai = 0;

    if(techs.containsKey("Spring Boot"))
        backend += 35;

    if(techs.containsKey("JWT"))
        backend += 15;

    if(techs.containsKey("JDBC"))
        backend += 10;

    if(techs.containsKey("React"))
        frontend += 40;

    if(techs.containsKey("MySQL"))
        database += 40;

    if(techs.containsKey("Gemini API"))
        ai += 40;

    int overall =
            (backend + frontend +
                    database + ai) / 4;

    DeveloperScoreDTO dto =
            new DeveloperScoreDTO();

    dto.setBackendScore(backend);
    dto.setFrontendScore(frontend);
    dto.setDatabaseScore(database);
    dto.setAiScore(ai);
    dto.setOverallScore(overall);

    return dto;
}
}