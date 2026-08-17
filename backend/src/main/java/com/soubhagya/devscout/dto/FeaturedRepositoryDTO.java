package com.soubhagya.devscout.dto;

import lombok.Data;

@Data
public class FeaturedRepositoryDTO {

    private String name;

    private String description;

    private String language;

    private int stars;

    private int forks;

    private String updatedAt;

    private boolean fork;
}