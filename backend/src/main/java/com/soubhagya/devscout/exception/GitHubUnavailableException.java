package com.soubhagya.devscout.exception;

public class GitHubUnavailableException extends RuntimeException {
    public GitHubUnavailableException(String message) {
        super(message);
    }
    public GitHubUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
