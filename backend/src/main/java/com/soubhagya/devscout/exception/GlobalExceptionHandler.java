package com.soubhagya.devscout.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GitHubUserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(GitHubUserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "GitHub user not found",
                        "message", ex.getMessage(),
                        "code", "USER_NOT_FOUND",
                        "status", 404
                ));
    }

    @ExceptionHandler(GitHubRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(GitHubRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "error", "GitHub rate limit exceeded",
                        "message", ex.getMessage(),
                        "code", "RATE_LIMITED",
                        "status", 429
                ));
    }

    @ExceptionHandler(GitHubAuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(GitHubAuthException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "GitHub authentication failed",
                        "message", ex.getMessage(),
                        "code", "GITHUB_AUTH_FAILED",
                        "status", 503
                ));
    }

    @ExceptionHandler(GitHubUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(GitHubUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "GitHub is temporarily unavailable",
                        "message", ex.getMessage(),
                        "code", "GITHUB_UNAVAILABLE",
                        "status", 503
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Invalid request",
                        "message", ex.getMessage(),
                        "code", "BAD_REQUEST",
                        "status", 400
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal server error",
                        "message", "An unexpected error occurred. Please try again.",
                        "code", "INTERNAL_ERROR",
                        "status", 500
                ));
    }
}
