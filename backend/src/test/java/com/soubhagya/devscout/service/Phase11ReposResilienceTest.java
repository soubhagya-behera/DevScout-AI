package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.exception.GitHubAuthException;
import com.soubhagya.devscout.exception.GitHubRateLimitException;
import com.soubhagya.devscout.exception.GitHubUnavailableException;
import com.soubhagya.devscout.exception.GitHubUserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 11: GitHub repository-list HTTP resilience.
 * Verifies explicit timeouts, exactly-one retry on transient network failures only,
 * and preserved HTTP-status exception mapping (no retry on status errors).
 * All network failures are simulated via mocks; no real HTTP, no timing waits.
 */
class Phase11ReposResilienceTest {

    private TechnologyDetector detector;
    private DeveloperScoringService scoring;
    private DeveloperProfileService profile;
    private GeminiService gemini;

    @BeforeEach
    void setUp() {
        detector = new TechnologyDetector();
        scoring = new DeveloperScoringService(detector);
        profile = new DeveloperProfileService(detector);
        gemini = mock(GeminiService.class);
    }

    private GitHubService serviceWithToken() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        ReflectionTestUtils.setField(svc, "githubToken", "test-token");
        // keep suite fast: skip the real 400ms backoff, asserted separately via constant
        doNothing().when(svc).sleepBeforeRetry();
        return svc;
    }

    private GitHubRepoDTO repo(String name) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription("desc");
        r.setLanguage("Java");
        r.setSize(100);
        return r;
    }

    private HttpClientErrorException statusError(HttpStatus status, String body) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY, bytes, StandardCharsets.UTF_8);
    }

    @Test
    void successOnFirstAttemptMakesExactlyOneRequest() {
        GitHubService svc = serviceWithToken();
        List<GitHubRepoDTO> repos = new ArrayList<>(List.of(repo("a")));
        doReturn(ResponseEntity.ok(repos)).when(svc).fetchReposResponse(anyString(), any());

        List<GitHubRepoDTO> result = svc.getRepositories("someuser");

        assertEquals(1, result.size());
        assertEquals("a", result.get(0).getName());
        verify(svc, times(1)).fetchReposResponse(anyString(), any());
    }

    @Test
    void transientNetworkFailureRetriesOnceThenSucceeds() {
        GitHubService svc = serviceWithToken();
        List<GitHubRepoDTO> repos = new ArrayList<>(List.of(repo("a")));
        doThrow(new ResourceAccessException("Connection timed out"))
                .doReturn(ResponseEntity.ok(repos))
                .when(svc).fetchReposResponse(anyString(), any());

        List<GitHubRepoDTO> result = svc.getRepositories("someuser");

        assertEquals(1, result.size());
        verify(svc, times(2)).fetchReposResponse(anyString(), any());
    }

    @Test
    void persistentNetworkFailureThrowsUnavailableAfterExactlyTwoAttempts() {
        GitHubService svc = serviceWithToken();
        doThrow(new ResourceAccessException("Connection timed out"))
                .when(svc).fetchReposResponse(anyString(), any());

        assertThrows(GitHubUnavailableException.class, () -> svc.getRepositories("someuser"));
        verify(svc, times(2)).fetchReposResponse(anyString(), any());
    }

    @Test
    void httpStatusErrorsAreNeverRetried() {
        // 404 -> not found, no retry
        GitHubService s404 = serviceWithToken();
        doThrow(statusError(HttpStatus.NOT_FOUND, "Not Found"))
                .when(s404).fetchReposResponse(anyString(), any());
        assertThrows(GitHubUserNotFoundException.class, () -> s404.getRepositories("ghost"));
        verify(s404, times(1)).fetchReposResponse(anyString(), any());

        // 429 -> rate limited, no retry
        GitHubService s429 = serviceWithToken();
        doThrow(statusError(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded"))
                .when(s429).fetchReposResponse(anyString(), any());
        assertThrows(GitHubRateLimitException.class, () -> s429.getRepositories("busy"));
        verify(s429, times(1)).fetchReposResponse(anyString(), any());

        // 403 plain -> auth failure, no retry
        GitHubService s403 = serviceWithToken();
        doThrow(statusError(HttpStatus.FORBIDDEN, "forbidden"))
                .when(s403).fetchReposResponse(anyString(), any());
        assertThrows(GitHubAuthException.class, () -> s403.getRepositories("locked"));
        verify(s403, times(1)).fetchReposResponse(anyString(), any());

        // 403 rate-limit body -> rate limited, no retry
        GitHubService s403r = serviceWithToken();
        doThrow(statusError(HttpStatus.FORBIDDEN, "API rate limit exceeded"))
                .when(s403r).fetchReposResponse(anyString(), any());
        assertThrows(GitHubRateLimitException.class, () -> s403r.getRepositories("busy2"));
        verify(s403r, times(1)).fetchReposResponse(anyString(), any());

        // 401 -> existing pass-through behavior preserved, no retry
        GitHubService s401 = serviceWithToken();
        doThrow(statusError(HttpStatus.UNAUTHORIZED, "Bad credentials"))
                .when(s401).fetchReposResponse(anyString(), any());
        assertThrows(HttpClientErrorException.class, () -> s401.getRepositories("bad"));
        verify(s401, times(1)).fetchReposResponse(anyString(), any());
    }

    @Test
    void restTemplateHasExplicitTimeoutsAndSingleRetryBudget() {
        GitHubService svc = serviceWithToken();
        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory)
                svc.getRestTemplateForTests().getRequestFactory();
        // Spring 6 exposes no getters; read the wired factory state directly
        assertEquals(5000, (int) ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(15000, (int) ReflectionTestUtils.getField(factory, "readTimeout"));
        assertEquals(2, GitHubService.MAX_REPOS_ATTEMPTS);
        assertEquals(400, GitHubService.REPOS_RETRY_BACKOFF_MS);
    }
}
