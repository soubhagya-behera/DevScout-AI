package com.soubhagya.devscout.dto;

import lombok.Data;

@Data
public class RepositoryEvidence {
    private String name;
    private boolean fork;
    private boolean archived;
    private int size;
    private String updatedAt;
    private String pushedAt;
    private int stars;
    private String language;
    private String recency; // RECENT / ACTIVE / STALE / UNKNOWN
    private boolean meaningful;
    private int qualityScore; // 0-100 deterministic
    private String evidenceSummary;
}
