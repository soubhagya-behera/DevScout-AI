package com.soubhagya.devscout.dto;

import lombok.Data;
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
}