package com.soubhagya.devscout.dto;

public enum DeveloperProfileType {
    BACKEND_DEVELOPER("Backend Developer"),
    FRONTEND_DEVELOPER("Frontend Developer"),
    FULL_STACK_DEVELOPER("Full Stack Developer"),
    AI_ML_DEVELOPER("AI/ML Developer"),
    DATA_BACKEND_DEVELOPER("Data/Backend Developer"),
    GENERAL_SOFTWARE_DEVELOPER("General Software Developer"),
    UNKNOWN("Unknown");

    private final String display;

    DeveloperProfileType(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
