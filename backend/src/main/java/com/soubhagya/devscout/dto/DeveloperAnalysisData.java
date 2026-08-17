package com.soubhagya.devscout.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DeveloperAnalysisData {

    private String username;

    private int totalRepositories;

    private Map<String,Integer> languages;

    private Map<String,Integer> technologies;

    private int backendScore;
    private int frontendScore;
    private int databaseScore;
    private int aiScore;
    private int overallScore;

    private List<String> repositorySummaries;
}