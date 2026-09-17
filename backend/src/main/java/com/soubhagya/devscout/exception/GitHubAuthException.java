package com.soubhagya.devscout.exception;

public class GitHubAuthException extends RuntimeException {
    public GitHubAuthException(String message) {
        super(message);
    }
    public GitHubAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
