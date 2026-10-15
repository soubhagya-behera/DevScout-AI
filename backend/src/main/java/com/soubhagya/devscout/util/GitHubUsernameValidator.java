package com.soubhagya.devscout.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.web.util.UriUtils;

/**
 * Centralized GitHub username validation.
 * GitHub rules: 1-39 chars, alphanumeric or single hyphens,
 * cannot begin or end with a hyphen, no consecutive hyphens. Case-insensitive.
 */
public final class GitHubUsernameValidator {

    private static final Pattern USERNAME =
            Pattern.compile("^(?!.*--)[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?$");

    private GitHubUsernameValidator() {
    }

    static String normalize(String username) {
        if (username == null) return "";
        return username.trim().toLowerCase();
    }

    /**
     * Validates and returns the normalized username.
     * @throws IllegalArgumentException with a generic message (mapped to 400 BAD_REQUEST)
     */
    public static String validate(String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty() || normalized.length() > 39 || !USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid GitHub username");
        }
        return normalized;
    }

    public static boolean isValid(String username) {
        String normalized = normalize(username);
        return !normalized.isEmpty() && normalized.length() <= 39 && USERNAME.matcher(normalized).matches();
    }

    /** Defense-in-depth encoding for URL path segments. */
    public static String encodePathSegment(String segment) {
        if (segment == null) return "";
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }
}
