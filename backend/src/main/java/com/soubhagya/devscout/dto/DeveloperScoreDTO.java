package com.soubhagya.devscout.dto;

import lombok.Data;

@Data
public class DeveloperScoreDTO {

    private int backendScore;
    private int frontendScore;
    private int databaseScore;
    private int aiScore;
    private int overallScore;
}