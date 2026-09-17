package com.soubhagya.devscout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedRepositoryDTO {

    private String name;

    private String description;

    private String language;

    private int stars;

    private int forks;

    private String updatedAt;

    private boolean fork;

    private boolean archived;

    private String recency;

    private List<String> technologies;
}