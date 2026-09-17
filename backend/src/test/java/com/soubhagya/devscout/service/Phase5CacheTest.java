package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase5CacheTest {

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

    private GitHubRepoDTO repo(String name) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription("Java Spring Boot");
        r.setLanguage("Java");
        r.setStargazers_count(1);
        r.setSize(100);
        return r;
    }

    private GitHubService createService(int maxEntries) {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        svc.setMaxEntriesForTests(maxEntries);
        // stub repo fetch to avoid HTTP
        doAnswer(inv -> new ArrayList<>(List.of(repo("r1")))).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidence(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));
        return svc;
    }

    @Test
    void maxCacheSizeIsEnforced() {
        GitHubService svc = createService(3);
        svc.getOrGenerateReport("user1");
        svc.getOrGenerateReport("user2");
        svc.getOrGenerateReport("user3");
        assertEquals(3, svc.cacheSizeForTests());
        svc.getOrGenerateReport("user4");
        assertEquals(3, svc.cacheSizeForTests(), "Cache should stay at max 3 after eviction");
        assertTrue(svc.cacheSizeForTests() <= 3);
    }

    @Test
    void lruEvictionWorks() throws InterruptedException {
        GitHubService svc = createService(3);
        svc.getOrGenerateReport("a");
        Thread.sleep(5);
        svc.getOrGenerateReport("b");
        Thread.sleep(5);
        svc.getOrGenerateReport("c");
        // access a to make it MRU
        Thread.sleep(5);
        svc.getOrGenerateReport("a");
        long aAccess = svc.getLastAccessForTests("a");
        long bAccess = svc.getLastAccessForTests("b");
        assertTrue(aAccess > bAccess, "Cache hit should update recency");
        // insert d -> should evict b (least recently used among b,c,a -> b oldest)
        svc.getOrGenerateReport("d");
        assertEquals(3, svc.cacheSizeForTests());
        // b should be evicted, a and c and d remain
        assertNotEquals(-1, svc.getLastAccessForTests("a"), "a should still be cached");
        assertNotEquals(-1, svc.getLastAccessForTests("c"), "c should still be cached");
        assertNotEquals(-1, svc.getLastAccessForTests("d"), "d should be cached");
        assertEquals(-1, svc.getLastAccessForTests("b"), "b should have been evicted as LRU");
    }

    @Test
    void expiredEntriesEvictedBeforeLru() {
        GitHubService svc = createService(2);
        svc.getOrGenerateReport("exp1");
        svc.getOrGenerateReport("exp2");
        assertEquals(2, svc.cacheSizeForTests());
        // expire exp1
        svc.expireForTests("exp1");
        // insert new user3 -> should evict expired exp1, not exp2 (which is valid LRU)
        svc.getOrGenerateReport("user3");
        assertEquals(2, svc.cacheSizeForTests());
        assertEquals(-1, svc.getLastAccessForTests("exp1"), "expired should be evicted first");
        assertNotEquals(-1, svc.getLastAccessForTests("exp2"), "valid entry should remain");
        assertNotEquals(-1, svc.getLastAccessForTests("user3"), "new entry should be present");
    }

    @Test
    void cacheHitUpdatesRecency() throws InterruptedException {
        GitHubService svc = createService(5);
        svc.getOrGenerateReport("u1");
        long t1 = svc.getLastAccessForTests("u1");
        Thread.sleep(10);
        svc.getOrGenerateReport("u1"); // hit
        long t2 = svc.getLastAccessForTests("u1");
        assertTrue(t2 > t1, "Hit should update lastAccess");
    }

    @Test
    void ttlExpirationTriggersRegeneration() {
        GitHubService svc = createService(5);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            return new ArrayList<>(List.of(repo("r1")));
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        svc.getOrGenerateReport("ttlUser");
        assertEquals(1, calls.get());
        svc.getOrGenerateReport("ttlUser");
        assertEquals(1, calls.get(), "hit should not call GitHub");
        svc.expireForTests("ttlUser");
        svc.getOrGenerateReport("ttlUser");
        assertEquals(2, calls.get(), "expired should regenerate");
    }

    @Test
    void concurrentSameUserCacheMissSingleGeneration() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        svc.setMaxEntriesForTests(500);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            Thread.sleep(200);
            return new ArrayList<>(List.of(repo("r1")));
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        int threads = 5;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FinalReportDTO>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) futures.add(exec.submit(() -> {
            start.await();
            return svc.getOrGenerateReport("concUser");
        }));
        start.countDown();
        for (Future<FinalReportDTO> f : futures) f.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(1, calls.get(), "same user concurrent miss should generate once");
    }

    @Test
    void concurrentExpiredSingleRegeneration() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        svc.setMaxEntriesForTests(500);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            Thread.sleep(100);
            return new ArrayList<>(List.of(repo("r1")));
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        svc.getOrGenerateReport("expConc");
        assertEquals(1, calls.get());
        svc.expireForTests("expConc");
        ExecutorService exec = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FinalReportDTO>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) futures.add(exec.submit(() -> {
            start.await();
            return svc.getOrGenerateReport("expConc");
        }));
        start.countDown();
        for (Future<FinalReportDTO> f : futures) f.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(2, calls.get(), "expired concurrent should regenerate exactly once");
    }

    @Test
    void differentUsersDoNotBlock() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        svc.setMaxEntriesForTests(500);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            calls.incrementAndGet();
            Thread.sleep(150);
            return new ArrayList<>(List.of(repo("r1")));
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        ExecutorService exec = Executors.newFixedThreadPool(2);
        Future<FinalReportDTO> f1 = exec.submit(() -> svc.getOrGenerateReport("userA"));
        Future<FinalReportDTO> f2 = exec.submit(() -> svc.getOrGenerateReport("userB"));
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(2, calls.get(), "different users should not block");
    }

    @Test
    void failedGenerationCleansInflight() {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        svc.setMaxEntriesForTests(500);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            int c = calls.incrementAndGet();
            if (c == 1) throw new com.soubhagya.devscout.exception.GitHubUserNotFoundException("fail");
            return new ArrayList<>(List.of(repo("r1")));
        }).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));

        assertThrows(com.soubhagya.devscout.exception.GitHubUserNotFoundException.class, () -> svc.getOrGenerateReport("failUser"));
        assertEquals(0, svc.inflightSizeForTests(), "failed should clean inflight");
        assertEquals(0, svc.cacheSizeForTests(), "failed should not create cache entry");
        // retry succeeds
        FinalReportDTO ok = svc.getOrGenerateReport("failUser");
        assertNotNull(ok);
        assertEquals(0, svc.inflightSizeForTests());
        assertEquals(1, svc.cacheSizeForTests());
    }

    @Test
    void cacheEvictionDoesNotAffectInflight() throws Exception {
        GitHubService svc = spy(new GitHubService(gemini, detector, scoring, profile));
        svc.setMaxEntriesForTests(2);
        doAnswer(inv -> new ArrayList<>(List.of(repo("r1")))).when(svc).getRepositories(anyString());
        doReturn(null).when(svc).fetchReadme(anyString(), anyString());
        doReturn(null).when(svc).fetchManifestEvidenceWithBudget(anyString(), anyString(), any(int[].class));
        svc.getOrGenerateReport("k1");
        svc.getOrGenerateReport("k2");
        assertEquals(2, svc.cacheSizeForTests());
        // start inflight for k3 with delay
        doAnswer(inv -> {
            Thread.sleep(200);
            return new ArrayList<>(List.of(repo("r1")));
        }).when(svc).getRepositories(anyString());
        ExecutorService exec = Executors.newFixedThreadPool(1);
        Future<FinalReportDTO> f3 = exec.submit(() -> svc.getOrGenerateReport("k3"));
        Thread.sleep(50);
        assertEquals(1, svc.inflightSizeForTests(), "k3 should be inflight");
        f3.get(5, TimeUnit.SECONDS);
        exec.shutdown();
        assertEquals(0, svc.inflightSizeForTests(), "inflight should be cleaned after success");
        assertTrue(svc.cacheSizeForTests() <= 2, "cache should remain bounded");
        // k3 should have evicted LRU and be present
        assertNotEquals(-1, svc.getLastAccessForTests("k3"));
    }
}
