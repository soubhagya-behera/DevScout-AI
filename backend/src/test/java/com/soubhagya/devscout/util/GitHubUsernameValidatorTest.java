package com.soubhagya.devscout.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubUsernameValidatorTest {

    @Test
    void acceptsValidUsernames() {
        assertEquals("octocat", GitHubUsernameValidator.validate("octocat"));
        assertEquals("octocat", GitHubUsernameValidator.validate("  OctoCat  "));
        assertEquals("a", GitHubUsernameValidator.validate("a"));
        assertEquals("a-b", GitHubUsernameValidator.validate("a-b"));
        assertEquals("user-123", GitHubUsernameValidator.validate("user-123"));
        assertTrue(GitHubUsernameValidator.isValid("torvalds"));
    }

    @Test
    void rejectsBlankAndNull() {
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate(null));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate(""));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("   "));
    }

    @Test
    void rejectsPathInjectionAndIllegalChars() {
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("foo..bar"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("foo?x=1"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("a b"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("invalid!user"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("user_name"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("-leading"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("trailing-"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("a".repeat(40)));
    }

    @Test
    void rejectsConsecutiveHyphens() {
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("foo--bar"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("a---b"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("--"));
        assertFalse(GitHubUsernameValidator.isValid("foo--bar"));
    }

    @Test
    void acceptsSingleHyphensAndLengthBoundaries() {
        assertEquals("valid-name", GitHubUsernameValidator.validate("valid-name"));
        assertEquals("a-b-c", GitHubUsernameValidator.validate("a-b-c"));
        assertTrue(GitHubUsernameValidator.isValid("a-1"));
        // one-character minimum
        assertEquals("a", GitHubUsernameValidator.validate("a"));
        assertEquals("0", GitHubUsernameValidator.validate("0"));
        // 39-character maximum is valid, 40 is not
        String max39 = "a" + "b".repeat(37) + "c";
        assertEquals(39, max39.length());
        assertEquals(max39, GitHubUsernameValidator.validate(max39));
        assertEquals("a" + "b-c".repeat(12) + "d", GitHubUsernameValidator.validate("a" + "b-c".repeat(12) + "d"));
        assertThrows(IllegalArgumentException.class, () -> GitHubUsernameValidator.validate("a".repeat(40)));
    }

    @Test
    void encodesPathSegments() {
        assertEquals("octocat", GitHubUsernameValidator.encodePathSegment("octocat"));
        // slash must not survive as a path separator
        assertFalse(GitHubUsernameValidator.encodePathSegment("foo/bar").contains("/"));
        // valid repo, manifest, and avatar segments pass through unchanged
        assertEquals("MyRepo", GitHubUsernameValidator.encodePathSegment("MyRepo"));
        assertEquals("my.repo", GitHubUsernameValidator.encodePathSegment("my.repo"));
        assertEquals("my_repo", GitHubUsernameValidator.encodePathSegment("my_repo"));
        assertEquals("package.json", GitHubUsernameValidator.encodePathSegment("package.json"));
        assertEquals("requirements.txt", GitHubUsernameValidator.encodePathSegment("requirements.txt"));
        assertEquals("valid-name", GitHubUsernameValidator.encodePathSegment("valid-name"));
    }
}
