package com.soubhagya.devscout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RepositoryAnalysisDTO {

    private String repositoryName;

    private String analysis;
}