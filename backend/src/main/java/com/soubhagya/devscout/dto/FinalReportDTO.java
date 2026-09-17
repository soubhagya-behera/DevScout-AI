package com.soubhagya.devscout.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FinalReportDTO {

    private String username;

    private int overallScore;
    private int backendScore;
    private int frontendScore;
    private int databaseScore;
    private int aiScore;

    private Map<String,Integer> technologies;

    private String aiAnalysis;

    // Phase 2: evidence-based profile (backward-compatible, nullable for old clients)
    private String profileType;
    private String confidence;
    private String specialization;
    private String experienceLevel;
    private String experienceEvidence;
    private Integer meaningfulRepositories;
    private Integer totalRepositories;
    private Integer totalStars;
    private Integer breadth;
    private Integer depth;
    private Integer distinctTechnologies;
    private Map<String,Integer> capabilitySignals;
    private String evidenceSummary;

    // Structured assessment (optional, for detailed consumers)
    private DeveloperProfileAssessment profileAssessment;

    // Phase 4: unified report fields (needed by dashboard without extra requests)
    private Map<String,Integer> languages;
    private String primaryLanguage;
    private List<FeaturedRepositoryDTO> featuredRepositories;
}