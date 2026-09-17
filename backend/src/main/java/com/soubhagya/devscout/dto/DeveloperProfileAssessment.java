package com.soubhagya.devscout.dto;

import lombok.Data;
import java.util.Map;

@Data
public class DeveloperProfileAssessment {

    private DeveloperProfileType profileType;
    private Confidence confidence;
    private String specialization; // dominant capability e.g., BACKEND, FRONTEND, FULL_STACK
    private int breadth; // number of capabilities with meaningful evidence
    private int depth; // max axis score
    private String experienceLevel; // Beginner/Intermediate/Advanced/Expert (deterministic, evidence-based)
    private String experienceEvidence; // short explanation of signals used

    private int meaningfulRepositories;
    private int totalRepositories;
    private int totalStars;
    private int distinctTechnologies;

    // capability -> signal count (repos)
    private Map<String, Integer> capabilitySignals;

    // for debugging/explainability
    private String evidenceSummary;
}
