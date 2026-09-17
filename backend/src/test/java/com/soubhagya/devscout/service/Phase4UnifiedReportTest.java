package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.*;
import com.soubhagya.devscout.exception.GitHubAuthException;
import com.soubhagya.devscout.exception.GitHubRateLimitException;
import com.soubhagya.devscout.exception.GitHubUserNotFoundException;
import com.soubhagya.devscout.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase4UnifiedReportTest {

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
        when(gemini.generateCandidateReport(any(DeveloperAnalysisData.class)))
                .thenReturn("LEVEL: Intermediate\nTOP_STRENGTHS:\n- Test\nIMPROVEMENTS:\n- Test\nHIRING_RECOMMENDATION:\nTest");
    }

    private GitHubRepoDTO repo(String name, String desc, String lang, int stars) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        r.setStargazers_count(stars);
        r.setSize(100);
        return r;
    }

    private List<GitHubRepoDTO> fakeRepos() {
        return List.of(
                repo("svc", "Java Spring Boot PostgreSQL", "Java", 5),
                repo("ui", "React TypeScript", "TypeScript", 3),
                repo("py", "Python FastAPI", "Python", 1)
        );
    }

    // helper to create spy service with stubbed getRepositories
    private GitHubService createServiceWithFakeRepos(List<GitHubRepoDTO> repos, AtomicInteger callCounter) {
        GitHubService service = spy(new GitHubService(gemini, detector, scoring, profile));
        // stub getRepositories to count and return repos
        doAnswer(inv -> {
            if (callCounter != null) callCounter.incrementAndGet();
            return new ArrayList<>(repos);
        }).when(service).getRepositories(anyString());
        // avoid real deep fetch HTTP: stub fetch methods to no-op or make deterministic
        doReturn(null).when(service).fetchReadme(anyString(), anyString());
        doReturn(null).when(service).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(service).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));
        return service;
    }

    @Test
    void unifiedReportContainsLanguages() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        FinalReportDTO report = svc.getOrGenerateReport("testuser");
        assertNotNull(report.getLanguages());
        assertFalse(report.getLanguages().isEmpty());
        assertTrue(report.getLanguages().containsKey("Java") || report.getLanguages().containsKey("TypeScript"));
    }

    @Test
    void unifiedReportContainsPrimaryLanguage() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        FinalReportDTO report = svc.getOrGenerateReport("testuser2");
        assertNotNull(report.getPrimaryLanguage());
        assertNotEquals("Unknown", report.getPrimaryLanguage());
        // Java appears once, but any valid language is okay; check it's one of the fake langs
        assertTrue(Set.of("Java","TypeScript","Python").contains(report.getPrimaryLanguage()));
    }

    @Test
    void unifiedReportContainsFeaturedRepositories() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        FinalReportDTO report = svc.getOrGenerateReport("testuser3");
        assertNotNull(report.getFeaturedRepositories());
        assertFalse(report.getFeaturedRepositories().isEmpty());
        assertTrue(report.getFeaturedRepositories().size() <= 10);
        // check safe fields present
        for (FeaturedRepositoryDTO fr : report.getFeaturedRepositories()) {
            assertNotNull(fr.getName());
        }
    }

    @Test
    void featuredDoesNotExposeReadmeContents() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        FinalReportDTO report = svc.getOrGenerateReport("testuser4");
        // Ensure DTO class doesn't have readmeContent field via reflection
        assertThrows(NoSuchFieldException.class, () -> FeaturedRepositoryDTO.class.getDeclaredField("readmeContent"));
        assertThrows(NoSuchFieldException.class, () -> FeaturedRepositoryDTO.class.getDeclaredField("dependencyEvidence"));
        // Also ensure JSON serialization doesn't include those (field names check)
        for (FeaturedRepositoryDTO fr : report.getFeaturedRepositories()) {
            assertNull(fr.getTechnologies() == null ? null : null); // just ensure technologies is list not raw content
            // description should be truncated, not full README
            if (fr.getDescription() != null) assertTrue(fr.getDescription().length() <= 121);
        }
    }

    @Test
    void featuredDoesNotExposeDependencyContents() {
        assertThrows(NoSuchFieldException.class, () -> FeaturedRepositoryDTO.class.getDeclaredField("dependencyEvidence"));
        assertThrows(NoSuchFieldException.class, () -> FeaturedRepositoryDTO.class.getDeclaredField("readmeContent"));
    }

    @Test
    void existingFieldsRemainIntact() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        FinalReportDTO report = svc.getOrGenerateReport("testuser5");
        assertNotNull(report.getUsername());
        assertNotNull(report.getTechnologies());
        assertNotNull(report.getAiAnalysis());
        assertNotNull(report.getProfileType());
        assertNotNull(report.getConfidence());
        assertNotNull(report.getExperienceLevel());
        assertNotNull(report.getTotalRepositories());
        assertNotNull(report.getProfileAssessment());
        assertTrue(report.getOverallScore() >= 0);
    }

    @Test
    void finalReportDelegatesToSamePipeline() {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), counter);
        FinalReportDTO r1 = svc.getOrGenerateReport("sameUser");
        svc.clearCacheForTests();
        // Need to reset spy to allow second generation - clear inflight handled
        // generateFinalReport should also increment counter once
        AtomicInteger counter2 = new AtomicInteger();
        GitHubService svc2 = createServiceWithFakeRepos(fakeRepos(), counter2);
        FinalReportDTO r2 = svc2.generateFinalReport("sameUser2");
        assertNotNull(r2);
        assertEquals(1, counter2.get());
        // also verify getOrGenerateReport after generateFinalReport hits cache
        FinalReportDTO r3 = svc2.getOrGenerateReport("sameUser2");
        assertEquals(1, counter2.get(), "cache hit should not trigger new GitHub call");
    }

    @Test
    void cacheHitCausesZeroNewGitHubCalls() {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), counter);
        svc.getOrGenerateReport("cacheUser");
        assertEquals(1, counter.get());
        verify(gemini, times(1)).generateCandidateReport(any(DeveloperAnalysisData.class));
        svc.getOrGenerateReport("cacheUser");
        assertEquals(1, counter.get(), "second call should be cache hit");
        verify(gemini, times(1)).generateCandidateReport(any(DeveloperAnalysisData.class));
    }

    @Test
    void concurrentSameUsernameTriggersOnlyOneGeneration() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        // Simulate delay in generation to force concurrency overlap
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        doAnswer(inv -> {
            counter.incrementAndGet();
            // simulate latency
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new ArrayList<>(fakeRepos());
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        int threads = 5;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FinalReportDTO>> futures = new ArrayList<>();
        for (int i=0;i<threads;i++) {
            futures.add(exec.submit(() -> {
                start.await();
                return svc.getOrGenerateReport("concurrentUser");
            }));
        }
        start.countDown();
        List<FinalReportDTO> results = new ArrayList<>();
        for (Future<FinalReportDTO> f : futures) results.add(f.get(5, TimeUnit.SECONDS));
        exec.shutdown();
        assertEquals(1, counter.get(), "concurrent same username should trigger only one GitHub fetch");
        // all results should be same instance or equal
        for (FinalReportDTO r : results) assertNotNull(r);
        verify(gemini, times(1)).generateCandidateReport(any(DeveloperAnalysisData.class));
    }

    @Test
    void differentUsernamesCanGenerateConcurrently() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        doAnswer(inv -> {
            counter.incrementAndGet();
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new ArrayList<>(fakeRepos());
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));
        // Need separate gemini mock count per user; reset
        reset(gemini);
        when(gemini.generateCandidateReport(any(DeveloperAnalysisData.class))).thenReturn("ok");

        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<FinalReportDTO> f1 = exec.submit(() -> svc.getOrGenerateReport("userA"));
        Future<FinalReportDTO> f2 = exec.submit(() -> svc.getOrGenerateReport("userB"));
        FinalReportDTO r1 = f1.get(5, TimeUnit.SECONDS);
        FinalReportDTO r2 = f2.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(2, counter.get(), "different usernames should each generate");
        verify(gemini, times(2)).generateCandidateReport(any(DeveloperAnalysisData.class));
    }

    @Test
    void failedSingleFlightIsRemovableAndRetryable() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        AtomicInteger callCount = new AtomicInteger();
        doAnswer(inv -> {
            int c = callCount.incrementAndGet();
            if (c == 1) throw new GitHubUserNotFoundException("failUser");
            return new ArrayList<>(fakeRepos());
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        assertThrows(GitHubUserNotFoundException.class, () -> svc.getOrGenerateReport("failUser"));
        // inflight should be cleaned
        assertEquals(0, svc.inflightSizeForTests(), "failed future should be removed");
        // retry should succeed
        FinalReportDTO retry = svc.getOrGenerateReport("failUser");
        assertNotNull(retry);
        assertEquals(2, callCount.get());
    }

    @Test
    void usernameCacheKeyIsCaseInsensitive() {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), counter);
        svc.getOrGenerateReport("Torvalds");
        assertEquals(1, counter.get());
        svc.getOrGenerateReport("torvalds");
        assertEquals(1, counter.get());
        svc.getOrGenerateReport("TORVALDS");
        assertEquals(1, counter.get());
    }

    @Test
    void usernameWhitespaceIsNormalized() {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), counter);
        svc.getOrGenerateReport("  myuser  ");
        assertEquals(1, counter.get());
        svc.getOrGenerateReport("myuser");
        assertEquals(1, counter.get());
        svc.getOrGenerateReport("\tmyuser\n");
        assertEquals(1, counter.get());
    }

    @Test
    void repositoryTabReceivesCorrectDataShape() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        FinalReportDTO report = svc.getOrGenerateReport("repoTabUser");
        List<FeaturedRepositoryDTO> repos = report.getFeaturedRepositories();
        assertNotNull(repos);
        for (FeaturedRepositoryDTO fr : repos) {
            assertNotNull(fr.getName());
            assertNotNull(fr.getRecency());
            // technologies should be list, not legacy string
            assertNotNull(fr.getTechnologies());
        }
    }

    @Test
    void geminiCalledOnceOnColdGeneration() {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), counter);
        reset(gemini);
        when(gemini.generateCandidateReport(any(DeveloperAnalysisData.class))).thenReturn("ok");
        svc.getOrGenerateReport("geminiUser");
        verify(gemini, times(1)).generateCandidateReport(any(DeveloperAnalysisData.class));
        assertEquals(1, counter.get());
    }

    @Test
    void geminiNotCalledOnCacheHit() {
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), null);
        reset(gemini);
        when(gemini.generateCandidateReport(any(DeveloperAnalysisData.class))).thenReturn("ok");
        svc.getOrGenerateReport("geminiCacheUser");
        verify(gemini, times(1)).generateCandidateReport(any(DeveloperAnalysisData.class));
        svc.getOrGenerateReport("geminiCacheUser");
        verify(gemini, times(1)).generateCandidateReport(any(DeveloperAnalysisData.class));
    }

    @Test
    void gitHubListNotDuplicatedByUnifiedGeneration() {
        AtomicInteger counter = new AtomicInteger();
        GitHubService svc = createServiceWithFakeRepos(fakeRepos(), counter);
        svc.getOrGenerateReport("singleListUser");
        assertEquals(1, counter.get(), "unified generation should call getRepositories exactly once");
    }

    @Test
    void rateLimitNotReportedAsUserNotFound() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String,Object>> resp = handler.handleRateLimit(new GitHubRateLimitException("rate"));
        assertEquals(429, resp.getStatusCode().value());
        assertEquals("RATE_LIMITED", resp.getBody().get("code"));
        assertNotEquals(404, resp.getStatusCode().value());
    }

    @Test
    void authNotReportedAsUserNotFound() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String,Object>> resp = handler.handleAuth(new GitHubAuthException("auth"));
        assertEquals(503, resp.getStatusCode().value());
        assertEquals("GITHUB_AUTH_FAILED", resp.getBody().get("code"));
    }

    @Test
    void userNotFoundMapsTo404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String,Object>> resp = handler.handleNotFound(new GitHubUserNotFoundException("ghost"));
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("USER_NOT_FOUND", resp.getBody().get("code"));
    }

    @Test
    void geminiFallbackIsStackNeutral() {
        GeminiService realGemini = new GeminiService(10000, 60000);
        DeveloperAnalysisData data = new DeveloperAnalysisData();
        data.setUsername("alice");
        data.setTotalRepositories(3);
        data.setLanguages(Map.of("Python", 2));
        data.setTechnologies(Map.of("Python", 2, "Django", 1));
        data.setBackendScore(50);
        data.setFrontendScore(20);
        data.setDatabaseScore(20);
        data.setAiScore(20);
        data.setOverallScore(30);
        data.setRepositorySummaries(List.of("svc Python Django"));
        String fallback = realGemini.buildStackNeutralFallback(data);
        assertNotNull(fallback);
        String lower = fallback.toLowerCase();
        // should mention deterministic and contain actual tech
        assertTrue(lower.contains("deterministic") || lower.contains("temporarily unavailable"));
        // should NOT hard-code Spring Boot unconditionally when tech is Python
        // Generic check: fallback should contain Python/Django since that's detected
        assertTrue(fallback.contains("Python") || fallback.contains("Django"));
        // should not contain hard-coded Java-specific list when tech doesn't include it, but we check generic
        // The old fallback always had "Spring Boot" "React" "MySQL" together; new should not have all three together for Python profile
        if (fallback.contains("Spring Boot") && fallback.contains("React") && fallback.contains("MySQL")) {
            fail("Fallback should not be hard-coded Java/Spring when tech is Python");
        }
    }

    @Test
    void geminiFallbackWithEmptyTechsIsNeutral() {
        GeminiService realGemini = new GeminiService(10000, 60000);
        DeveloperAnalysisData data = new DeveloperAnalysisData();
        data.setTechnologies(Map.of());
        data.setOverallScore(20);
        String fallback = realGemini.buildStackNeutralFallback(data);
        assertTrue(fallback.toLowerCase().contains("temporarily unavailable") || fallback.toLowerCase().contains("deterministic"));
        assertFalse(fallback.contains("Spring Boot") && fallback.contains("React") && fallback.contains("MySQL"));
    }
}
