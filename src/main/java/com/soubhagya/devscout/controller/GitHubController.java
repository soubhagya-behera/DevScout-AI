package com.soubhagya.devscout.controller;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.FinalReportDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.dto.ProfileAnalysisDTO;
import com.soubhagya.devscout.dto.TechnologyAnalysisDTO;
import com.soubhagya.devscout.service.GeminiService;
import com.soubhagya.devscout.service.GitHubService;
import org.springframework.web.bind.annotation.*;

import com.soubhagya.devscout.dto.RepositoryAnalysisDTO;
import java.util.ArrayList;

import java.util.List;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;
    private final GeminiService geminiService;

    public GitHubController(
        GitHubService gitHubService,
        GeminiService geminiService
) {
    this.gitHubService = gitHubService;
    this.geminiService = geminiService;
}

    @GetMapping("/{username}")
    public List<GitHubRepoDTO> getRepos(
            @PathVariable String username
    ) {
        return gitHubService.getRepositories(username);
    }

    @GetMapping("/analyze/{username}")
public ProfileAnalysisDTO analyze(
        @PathVariable String username
) {
    return gitHubService.analyzeProfile(
            username
    );
}

@GetMapping("/tech/{username}")
public TechnologyAnalysisDTO tech(
        @PathVariable String username
) {
    return gitHubService
            .detectTechnologies(
                    username
            );
}

@GetMapping("/score/{username}")
public DeveloperScoreDTO score(
        @PathVariable String username
) {
    return gitHubService.calculateScore(
            username
    );
}

@GetMapping("/gemini-test")
public String geminiTest() {

    return geminiService.testGemini();
}

@GetMapping("/analyze-repo")
public String analyzeRepo() {

    return geminiService.analyzeProject(
            "GreenCart",
            "Full Stack Grocery Delivery Platform built with Spring Boot, React, MySQL, JWT Authentication and Razorpay Payments."
    );
}

@GetMapping("/full-analysis/{username}")
public List<RepositoryAnalysisDTO> fullAnalysis(
        @PathVariable String username
) {

    List<GitHubRepoDTO> repos =
            gitHubService.getRepositories(
                    username
            );

    List<RepositoryAnalysisDTO> result =
            new ArrayList<>();

    for (GitHubRepoDTO repo : repos) {

        if (repo.getDescription() != null) {

            result.add(
                    geminiService
                            .analyzeRepository(
                                    repo
                            )
            );
        }
    }

    return result;
}

@GetMapping("/candidate-report/{username}")
public String candidateReport(
        @PathVariable String username
) {

    List<GitHubRepoDTO> repos =
            gitHubService.getRepositories(
                    username
            );

    StringBuilder summary =
            new StringBuilder();

    for (GitHubRepoDTO repo : repos) {

        summary.append("Repository: ")
                .append(repo.getName())
                .append("\n");

        summary.append("Description: ")
                .append(repo.getDescription())
                .append("\n\n");
    }

    return geminiService
            .generateCandidateReport(
                    summary.toString()
            );
}

@GetMapping("/final-report/{username}")
public FinalReportDTO finalReport(
        @PathVariable String username
) {

    String analysis =
            candidateReport(username);

    return gitHubService
            .generateFinalReport(
                    username,
                    analysis
            );
}
}