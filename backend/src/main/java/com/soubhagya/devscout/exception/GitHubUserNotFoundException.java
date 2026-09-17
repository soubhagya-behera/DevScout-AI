package com.soubhagya.devscout.exception;

public class GitHubUserNotFoundException extends RuntimeException {
    public GitHubUserNotFoundException(String username) {
        super("GitHub user not found: " + username);
    }
    public GitHubUserNotFoundException(String username, Throwable cause) {
        super("GitHub user not found: " + username, cause);
    }
}
