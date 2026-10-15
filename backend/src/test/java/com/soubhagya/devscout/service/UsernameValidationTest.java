package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperAnalysisData;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsernameValidationTest {

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
        when(gemini.generateCandidateReport(any(DeveloperAnalysisData.class))).thenReturn(
                "LEVEL: Test\nTOP_STRENGTHS:\n- Test\nIMPROVEMENTS:\n- Test\nHIRING_RECOMMENDATION:\nTest");
    }

    @Test
    void getRepositoriesRejectsInvalidWithoutHttp() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        for (String bad : new String[]{null, "", "   ", "foo/bar", "foo?x=1", "a b", "invalid!user", "-bad", "bad-", "foo--bar", "a".repeat(40)}) {
            assertThrows(IllegalArgumentException.class, () -> svc.getRepositories(bad),
                    "should reject: " + bad);
        }
        verify(svc, never()).fetchReposResponse(anyString(), any(HttpEntity.class));
    }

    @Test
    void getOrGenerateReportRejectsBeforeCacheAndSingleFlight() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        assertThrows(IllegalArgumentException.class, () -> svc.getOrGenerateReport("foo/bar"));
        assertThrows(IllegalArgumentException.class, () -> svc.getOrGenerateReport("  "));
        assertEquals(0, svc.cacheSizeForTests(), "invalid input must not populate cache");
        assertEquals(0, svc.inflightSizeForTests(), "invalid input must not leave single-flight entries");
        verify(svc, never()).fetchReposResponse(anyString(), any(HttpEntity.class));
        verify(svc, never()).getRepositories(anyString());
    }

    @Test
    void getProfileRejectsInvalid() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        assertThrows(IllegalArgumentException.class, () -> svc.getProfile("invalid!user"));
        verify(svc, never()).fetchReposResponse(anyString(), any(HttpEntity.class));
    }

    @Test
    void validUsernameIsNormalizedAndEncoded() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        doReturn(ResponseEntity.ok(List.<GitHubRepoDTO>of())).when(svc)
                .fetchReposResponse(anyString(), any(HttpEntity.class));
        svc.getRepositories("  OctoCat ");
        verify(svc).fetchReposResponse(
                argThat((String url) -> url.equals("https://api.github.com/users/octocat/repos?per_page=100")),
                any(HttpEntity.class));
    }

    @Test
    void singleHyphenAndBoundaryUsernamesAreAccepted() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        doReturn(ResponseEntity.ok(List.<GitHubRepoDTO>of())).when(svc)
                .fetchReposResponse(anyString(), any(HttpEntity.class));
        svc.getRepositories("valid-name");
        verify(svc).fetchReposResponse(
                argThat((String url) -> url.equals("https://api.github.com/users/valid-name/repos?per_page=100")),
                any(HttpEntity.class));
        // one-character and 39-character usernames flow through unchanged
        svc.getRepositories("a");
        verify(svc).fetchReposResponse(
                argThat((String url) -> url.equals("https://api.github.com/users/a/repos?per_page=100")),
                any(HttpEntity.class));
        String max39 = "a" + "b".repeat(37) + "c";
        svc.getRepositories(max39);
        verify(svc).fetchReposResponse(
                argThat((String url) -> url.equals("https://api.github.com/users/" + max39 + "/repos?per_page=100")),
                any(HttpEntity.class));
    }
}
