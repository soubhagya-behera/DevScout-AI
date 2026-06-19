package com.soubhagya.devscout.controller;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.dto.ProfileAnalysisDTO;
import com.soubhagya.devscout.dto.TechnologyAnalysisDTO;
import com.soubhagya.devscout.service.GeminiService;
import com.soubhagya.devscout.service.GitHubService;
import org.springframework.web.bind.annotation.*;

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
}