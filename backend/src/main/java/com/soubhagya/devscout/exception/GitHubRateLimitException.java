package com.soubhagya.devscout.exception;

public class GitHubRateLimitException extends RuntimeException {
    public GitHubRateLimitException(String message) {
        super(message);
    }
    public GitHubRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
