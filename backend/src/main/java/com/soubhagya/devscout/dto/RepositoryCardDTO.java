package com.soubhagya.devscout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryCardDTO {

    private String name;

    private String description;

    private String language;

    private int stars;
}