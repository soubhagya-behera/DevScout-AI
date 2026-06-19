package com.soubhagya.devscout.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ProfileAnalysisDTO {

    private int totalRepositories;

    private Map<String,Integer> languages;
}