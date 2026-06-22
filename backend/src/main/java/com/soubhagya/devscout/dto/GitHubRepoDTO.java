package com.soubhagya.devscout.dto;

import lombok.Data;

@Data
public class GitHubRepoDTO {

    private String name;

    private String description;

    private String language;

    private int stargazers_count;

    public int getStars() {
    return stargazers_count;
}
}

