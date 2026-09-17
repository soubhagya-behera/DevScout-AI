package com.soubhagya.devscout.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepoDTO {

    private String name;

    private String description;

    private String language;

    private int stargazers_count;

    public int getStars() {
    return stargazers_count;
    }

    // Phase 3: metadata already available from GET /users/{u}/repos?per_page=100 — zero extra calls
    private boolean fork;
    private boolean archived;
    private int size;
    private String updated_at;
    private String pushed_at;

    @JsonProperty("forks_count")
    private int forks_count;
    @JsonProperty("open_issues_count")
    private int open_issues_count;

    private List<String> topics;

    // Phase 3 deep evidence (transient, populated via limited deep fetches, not from list API)
    // Truncated README content (max 2KB) — used only for technology detection
    private String readmeContent;
    // Concatenated dependency manifest evidence (e.g., package.json snippet) — used for detection
    private String dependencyEvidence;
}

