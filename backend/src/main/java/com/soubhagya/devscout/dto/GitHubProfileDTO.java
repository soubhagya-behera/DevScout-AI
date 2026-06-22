package com.soubhagya.devscout.dto;

import lombok.Data;

@Data
public class GitHubProfileDTO {

    private String username;

    private int totalRepositories;

    private String primaryLanguage;
}