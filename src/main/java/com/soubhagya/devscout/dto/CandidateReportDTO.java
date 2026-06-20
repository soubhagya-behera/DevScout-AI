package com.soubhagya.devscout.dto;

import lombok.Data;

@Data
public class CandidateReportDTO {

    private int overallScore;

    private int backendScore;

    private int frontendScore;

    private int databaseScore;

    private int aiScore;

    private String aiAnalysis;
}