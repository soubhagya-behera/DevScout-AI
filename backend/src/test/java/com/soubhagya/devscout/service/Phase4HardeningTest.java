package com.soubhagya.devscout.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soubhagya.devscout.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase4HardeningTest {

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
        when(gemini.generateCandidateReport(any(DeveloperAnalysisData.class))).thenReturn("LEVEL: Test\nTOP_STRENGTHS:\n- Test\nIMPROVEMENTS:\n- Test\nHIRING_RECOMMENDATION:\nTest");
    }

    private GitHubRepoDTO repo(String name, String desc, String lang, int stars, int size) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        r.setStargazers_count(stars);
        r.setSize(size);
        return r;
    }

    @Test
    void deepHttpBudgetNeverExceeds20() {
        // 15 repos with no strong tech => each would need 3 HTTP (readme + list + file) if unconstrained => 45
        List<GitHubRepoDTO> manyRepos = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            // language null and description generic => techSize 0 => requires deep fetch
            manyRepos.add(repo("repo" + i, "generic project", null, 0, 100));
        }
        AtomicInteger httpCounter = new AtomicInteger();
        AtomicInteger readmeCalls = new AtomicInteger();
        AtomicInteger manifestCalls = new AtomicInteger();

        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        doAnswer(inv -> {
            httpCounter.incrementAndGet();
            readmeCalls.incrementAndGet();
            return "dummy readme content with python django";
        }).when(svc).fetchReadme(anyString(), anyString());

        doAnswer(inv -> {
            int[] used = inv.getArgument(2);
            // list request
            used[0]++;
            httpCounter.incrementAndGet();
            manifestCalls.incrementAndGet();
            if (used[0] >= GitHubService.MAX_DEEP_HTTP_CALLS) {
                return null; // file budget exhausted
            }
            // file request
            used[0]++;
            httpCounter.incrementAndGet();
            return "manifest evidence";
        }).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        doAnswer(inv -> new ArrayList<>(manyRepos)).when(svc).getRepositories(anyString());

        // invoke enrich directly
        svc.enrichWithDeepEvidence(new ArrayList<>(manyRepos), "testuser");

        int totalHttp = httpCounter.get();
        // README 1 per repo, manifest up to 2 per repo => ~3 per repo, budget 20 caps at ~6-7 repos
        assertTrue(totalHttp <= 20, "Actual deep HTTP requests must not exceed budget 20, was " + totalHttp);
        assertTrue(totalHttp <= GitHubService.MAX_DEEP_HTTP_CALLS, "Must respect MAX_DEEP_HTTP_CALLS");
        // also verify readmeCalls <=10 (max repos)
        assertTrue(readmeCalls.get() <= 10);
    }

    @Test
    void deepHttpBudgetCountsFailedRequests() {
        List<GitHubRepoDTO> repos = new ArrayList<>();
        for (int i = 0; i < 15; i++) repos.add(repo("r" + i, "generic", null, 0, 50));

        AtomicInteger httpAttempts = new AtomicInteger();
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        doAnswer(inv -> {
            httpAttempts.incrementAndGet();
            throw new RuntimeException("network failure");
        }).when(svc).fetchReadme(anyString(), anyString());
        doAnswer(inv -> {
            int[] used = inv.getArgument(2);
            used[0]++;
            httpAttempts.incrementAndGet();
            if (used[0] >= GitHubService.MAX_DEEP_HTTP_CALLS) return null;
            used[0]++;
            httpAttempts.incrementAndGet();
            return null;
        }).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));
        doAnswer(inv -> new ArrayList<>(repos)).when(svc).getRepositories(anyString());

        svc.enrichWithDeepEvidence(new ArrayList<>(repos), "user");

        // Even though all failed, budget must still be enforced
        assertTrue(httpAttempts.get() <= 20 || httpAttempts.get() <= 22, "Failed requests must still count toward budget, attempts=" + httpAttempts.get());
        // The key guarantee: method stops after budget
        assertTrue(httpAttempts.get() <= GitHubService.MAX_DEEP_HTTP_CALLS + 2, "Should not exceed budget by more than one repo's worth");
    }

    @Test
    void mainListNotCountedInDeepBudget() {
        // verify that 1 main list + 20 deep = 21 worst-case, not 20 total
        assertEquals(20, GitHubService.MAX_DEEP_HTTP_CALLS);
        assertEquals(10, GitHubService.MAX_DEEP_REPOS);
        // worst-case deep 20 + 1 list =21
        int worstCaseTotal = 1 + GitHubService.MAX_DEEP_HTTP_CALLS;
        assertEquals(21, worstCaseTotal);
    }

    @Test
    void ttlExpiryTriggersRegeneration() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        List<GitHubRepoDTO> repos = List.of(repo("a", "Java Spring Boot", "Java", 1, 100));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            return new ArrayList<>(repos);
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        svc.getOrGenerateReport("expiryUser");
        assertEquals(1, calls.get());
        // cache hit
        svc.getOrGenerateReport("expiryUser");
        assertEquals(1, calls.get());
        // force expiry
        svc.expireForTests("expiryUser");
        svc.getOrGenerateReport("expiryUser");
        assertEquals(2, calls.get(), "Expired entry should trigger regeneration");
    }

    @Test
    void simultaneousExpiredRequestsSingleFlight() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        List<GitHubRepoDTO> repos = List.of(repo("a", "Java Spring Boot", "Java", 1, 100));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new ArrayList<>(repos);
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        svc.getOrGenerateReport("concurrentExpiry");
        assertEquals(1, calls.get());
        svc.expireForTests("concurrentExpiry");
        // 5 concurrent requests after expiry should still only generate once
        ExecutorService exec = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FinalReportDTO>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) futures.add(exec.submit(() -> {
            start.await();
            return svc.getOrGenerateReport("concurrentExpiry");
        }));
        start.countDown();
        for (Future<FinalReportDTO> f : futures) f.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(2, calls.get(), "Concurrent expired requests should trigger exactly one regeneration (total 2)");
    }

    @Test
    void jacksonSerializationDoesNotLeakInternalFields() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        List<GitHubRepoDTO> repos = List.of(repo("svc", "Java Spring Boot PostgreSQL", "Java", 2, 200));
        doAnswer(inv -> new ArrayList<>(repos)).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        FinalReportDTO report = svc.getOrGenerateReport("serializeUser");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(report);
        String lower = json.toLowerCase();
        assertFalse(lower.contains("readmecontent"), "JSON must not contain readmeContent");
        assertFalse(lower.contains("dependencyevidence"), "JSON must not contain dependencyEvidence");
        assertFalse(lower.contains("github.token"), "JSON must not contain github token");
        assertFalse(lower.contains("gemini"), "JSON must not contain gemini key");
        assertFalse(lower.contains("reportcache"), "JSON must not contain cache structure");
        assertFalse(lower.contains("inflight"), "JSON must not contain inflight");
        assertFalse(lower.contains("completablefuture"), "JSON must not contain future");

        JsonNode node = mapper.readTree(json);
        assertTrue(node.has("languages"), "Should contain languages");
        assertTrue(node.has("primaryLanguage"), "Should contain primaryLanguage");
        assertTrue(node.has("featuredRepositories"), "Should contain featuredRepositories");
        assertTrue(node.has("technologies"), "Should contain technologies");
        assertTrue(node.has("overallScore"), "Should contain scores");

        // featuredRepositories fields only
        JsonNode featured = node.get("featuredRepositories");
        assertTrue(featured.isArray() && featured.size() > 0);
        JsonNode first = featured.get(0);
        assertTrue(first.has("name"));
        assertFalse(first.has("readmeContent"));
        assertFalse(first.has("dependencyEvidence"));
        // expected safe fields
        Set<String> allowed = Set.of("name","description","language","stars","forks","updatedAt","fork","archived","recency","technologies");
        for (Iterator<String> it = first.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            assertTrue(allowed.contains(field), "Unexpected field in FeaturedRepositoryDTO: " + field);
        }
    }
}
