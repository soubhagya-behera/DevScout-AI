package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperProfileType;
import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10 regression for observed 18-repo broad profile.
 * Ensures diminishing returns + quality + correct AI classification.
 */
class Phase10CalibrationTest {

    private TechnologyDetector detector;
    private DeveloperScoringService scoring;
    private DeveloperProfileService profile;

    @BeforeEach
    void setUp() {
        detector = new TechnologyDetector();
        scoring = new DeveloperScoringService(detector);
        profile = new DeveloperProfileService(detector);
    }

    private GitHubRepoDTO repo(String name, String desc, String lang, int size) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        r.setSize(size);
        r.setStargazers_count(0);
        return r;
    }

    private List<GitHubRepoDTO> observedFixture() {
        List<GitHubRepoDTO> repos = new ArrayList<>();
        for (int i = 0; i < 8; i++) repos.add(repo("spring" + i, "Java Spring Boot", "Java", 200));
        for (int i = 0; i < 8; i++) repos.add(repo("react" + i, "React JavaScript", "JavaScript", 150));
        for (int i = 0; i < 6; i++) repos.add(repo("mysql" + i, "MySQL database", "Java", 100));
        for (int i = 0; i < 3; i++) repos.add(repo("jdbc" + i, "JDBC", "Java", 80));
        for (int i = 0; i < 2; i++) repos.add(repo("pg" + i, "PostgreSQL", "Java", 80));
        for (int i = 0; i < 2; i++) repos.add(repo("jwt" + i, "JWT authentication", "Java", 80));
        for (int i = 0; i < 2; i++) repos.add(repo("redis" + i, "Redis cache", "Java", 80));
        for (int i = 0; i < 2; i++) repos.add(repo("docker" + i, "Docker container", "Java", 80));
        for (int i = 0; i < 2; i++) repos.add(repo("gemini" + i, "Gemini API AI assistant", "Java", 80));
        // Trim to 18 meaningful as per fixture (take first 18)
        return repos.subList(0, 18);
    }

    @Test
    void observedBroadProfileDoesNotSaturate() {
        var repos = observedFixture();
        var score = scoring.scoreFromRepos(repos);
        // Must not be 100 saturated — per-repo max prevents 12 signals from 18 repos saturating
        assertTrue(score.getBackendScore() < 100, "Backend should not saturate to 100, was " + score.getBackendScore());
        assertTrue(score.getFrontendScore() < 100, "Frontend should not saturate to 100, was " + score.getFrontendScore());
        assertTrue(score.getDatabaseScore() < 100, "Database should not saturate to 100, was " + score.getDatabaseScore());
        assertTrue(score.getOverallScore() < 90, "Overall should not be 95 saturated, was " + score.getOverallScore());
        // Per-repo max: 8 backend repos -> ~54, 8 frontend -> ~54, still meaningful but not 100
        assertTrue(score.getBackendScore() >= 45, "Backend should still show strong evidence, was " + score.getBackendScore());
        assertTrue(score.getFrontendScore() >= 40, "Frontend should show evidence, was " + score.getFrontendScore());
        assertTrue(score.getOverallScore() >= 45, "Overall should be credible, was " + score.getOverallScore());
    }

    @Test
    void aiNotClassifiedFromGeminiApiOnly() {
        var repos = observedFixture(); // contains 0 or 2 Gemini API within first 18? first 18 has 14 Java +? Actually first 18 includes 8 spring +8 react +2 mysql =18, so Gemini not included -> AI should be low
        // Create fixture with only Gemini API
        List<GitHubRepoDTO> geminiOnly = List.of(
                repo("a", "Gemini API", "Java", 100),
                repo("b", "Gemini API", "Java", 100)
        );
        var techs = detector.detect(geminiOnly);
        var sc = scoring.scoreFromRepos(geminiOnly);
        var assessment = profile.assess(geminiOnly, techs, Map.of("Java", 2), sc);
        assertNotEquals(DeveloperProfileType.AI_ML_DEVELOPER, assessment.getProfileType(),
                "Gemini API alone must not be AI_ML, was " + assessment.getProfileType());
        // With real ML lib should be AI_ML
        var mlRepos = List.of(
                repo("ml1", "Python PyTorch", "Python", 200),
                repo("ml2", "Python TensorFlow scikit-learn", "Python", 200)
        );
        var techs2 = detector.detect(mlRepos);
        var sc2 = scoring.scoreFromRepos(mlRepos);
        var a2 = profile.assess(mlRepos, techs2, Map.of("Python", 2), sc2);
        assertEquals(DeveloperProfileType.AI_ML_DEVELOPER, a2.getProfileType());
    }

    @Test
    void evidenceStrengthWeightingMatters() {
        // STRONG 1.0 vs WEAK 0.30 must differ
        assertEquals(1.0, detector.weightForStrength(TechnologyDetector.EvidenceStrength.STRONG), 0.001);
        assertEquals(0.65, detector.weightForStrength(TechnologyDetector.EvidenceStrength.MEDIUM), 0.001);
        assertEquals(0.30, detector.weightForStrength(TechnologyDetector.EvidenceStrength.WEAK), 0.001);
        assertTrue(detector.weightForStrength(TechnologyDetector.EvidenceStrength.STRONG)
                > detector.weightForStrength(TechnologyDetector.EvidenceStrength.MEDIUM));
        assertTrue(detector.weightForStrength(TechnologyDetector.EvidenceStrength.MEDIUM)
                > detector.weightForStrength(TechnologyDetector.EvidenceStrength.WEAK));
    }

    @Test
    void diminishingReturns() {
        var oneStrong = List.of(repo("s", "Java Spring Boot", "Java", 500));
        oneStrong.get(0).setPushed_at(java.time.Instant.now().toString());
        var tenShallow = new ArrayList<GitHubRepoDTO>();
        for (int i = 0; i < 10; i++) {
            var r = repo("shallow" + i, "Java", "Java", 10);
            r.setPushed_at(java.time.Instant.now().minusSeconds(800L*86400).toString());
            tenShallow.add(r);
        }
        var s1 = scoring.scoreFromRepos(oneStrong);
        var s10 = scoring.scoreFromRepos(tenShallow);
        assertTrue(s10.getBackendScore() < 90, "10 shallow should not saturate, was " + s10.getBackendScore());
        assertTrue(s1.getBackendScore() < s10.getBackendScore() + 30, "Diminishing: 10 shallow should not dominate 1 strong by huge margin");
        assertTrue(s1.getBackendScore() >= 28, "Single strong per-repo max ~28-30, was " + s1.getBackendScore());
    }

    @Test
    void repositoryQualityMattersForkStale() {
        var substantial = repo("sub", "Java Spring Boot PostgreSQL", "Java", 500);
        substantial.setStargazers_count(5);
        // make recent
        substantial.setPushed_at(java.time.Instant.now().toString());
        var shallowFork = repo("fork", "Java", "Java", 10);
        shallowFork.setFork(true);
        shallowFork.setPushed_at(java.time.Instant.now().minusSeconds(800L * 86400).toString());
        var scSub = scoring.scoreFromRepos(List.of(substantial));
        var scFork = scoring.scoreFromRepos(List.of(shallowFork));
        assertTrue(scSub.getBackendScore() > scFork.getBackendScore(),
                "Substantial original recent should score higher than shallow stale fork: sub " + scSub.getBackendScore() + " fork " + scFork.getBackendScore());
    }

    @Test
    void fullStackRequiresBothBackendAndFrontend() {
        var backendOnly = List.of(
                repo("b1", "Java Spring Boot", "Java", 200),
                repo("b2", "Java Spring Boot MySQL", "Java", 200)
        );
        var techs = detector.detect(backendOnly);
        var sc = scoring.scoreFromRepos(backendOnly);
        var a = profile.assess(backendOnly, techs, Map.of("Java", 2), sc);
        assertNotEquals(DeveloperProfileType.FULL_STACK_DEVELOPER, a.getProfileType());
        // Add frontend
        var full = new ArrayList<>(backendOnly);
        full.add(repo("f1", "React TypeScript", "TypeScript", 200));
        full.add(repo("f2", "React JavaScript", "JavaScript", 150));
        var techs2 = detector.detect(full);
        var sc2 = scoring.scoreFromRepos(full);
        var a2 = profile.assess(full, techs2, Map.of("Java", 2, "TypeScript", 1, "JavaScript", 1), sc2);
        assertEquals(DeveloperProfileType.FULL_STACK_DEVELOPER, a2.getProfileType());
    }

    @Test
    void crossStackFairness() {
        // Java, Python, Node, Go each with 2 repos should be similar
        var javaRepos = List.of(repo("j1", "Java Spring Boot MySQL", "Java", 200), repo("j2", "Java Spring Boot Redis", "Java", 200));
        var pyRepos = List.of(repo("p1", "Python Django PostgreSQL", "Python", 200), repo("p2", "Python FastAPI Redis", "Python", 200));
        var nodeRepos = List.of(repo("n1", "Node.js Express MongoDB", "JavaScript", 200), repo("n2", "Node.js Express Redis", "JavaScript", 200));
        var goRepos = List.of(repo("g1", "Go Gin PostgreSQL", "Go", 200), repo("g2", "Go Fiber Redis", "Go", 200));
        var sJava = scoring.scoreFromRepos(javaRepos).getBackendScore();
        var sPy = scoring.scoreFromRepos(pyRepos).getBackendScore();
        var sNode = scoring.scoreFromRepos(nodeRepos).getBackendScore();
        var sGo = scoring.scoreFromRepos(goRepos).getBackendScore();
        int max = Math.max(Math.max(sJava, sPy), Math.max(sNode, sGo));
        int min = Math.min(Math.min(sJava, sPy), Math.min(sNode, sGo));
        assertTrue(max - min <= 20, "Cross-stack fairness within 20, max " + max + " min " + min + " java " + sJava + " py " + sPy + " node " + sNode + " go " + sGo);
    }

    @Test
    void oneRepoCanContributeMultipleAxesButNotDominate() {
        var multi = repo("full", "Java Spring Boot React PostgreSQL", "Java", 300);
        multi.setPushed_at(java.time.Instant.now().toString());
        var sc = scoring.scoreFromRepos(List.of(multi));
        // Per-repo max: one repo contributes at most once per capability, so 1 backend/frontend/db each ~28-30
        assertTrue(sc.getBackendScore() < 70, "Single multi-tech repo backend <70, was " + sc.getBackendScore());
        assertTrue(sc.getFrontendScore() < 70, "Single multi-tech frontend <70");
        assertTrue(sc.getDatabaseScore() < 70, "Single multi-tech db <70");
        assertTrue(sc.getBackendScore() >= 25, "Should still show backend evidence, was " + sc.getBackendScore());
    }
}
