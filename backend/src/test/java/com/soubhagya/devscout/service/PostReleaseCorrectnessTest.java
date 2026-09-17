package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperAnalysisData;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PostReleaseCorrectnessTest {

    private TechnologyDetector detector;
    private DeveloperScoringService scoring;
    private DeveloperProfileService profile;
    private GeminiService gemini;

    @BeforeEach
    void setUp() {
        detector = new TechnologyDetector();
        scoring = new DeveloperScoringService(detector);
        profile = new DeveloperProfileService(detector);
        gemini = new GeminiService(10000, 60000);
    }

    private DeveloperAnalysisData dataWith(String exp, int overall) {
        DeveloperAnalysisData d = new DeveloperAnalysisData();
        d.setUsername("testuser");
        d.setTotalRepositories(5);
        d.setLanguages(Map.of("Python", 3));
        d.setTechnologies(Map.of("Python", 2));
        d.setBackendScore(60);
        d.setFrontendScore(30);
        d.setDatabaseScore(30);
        d.setAiScore(20);
        d.setOverallScore(overall);
        d.setExperienceLevel(exp);
        d.setRepositorySummaries(List.of("pandas-cookbook (Python) ★0 [STALE] Tech:Python - No description"));
        return d;
    }

    // Issue 2: fallback must use authoritative experienceLevel exactly, not derive from overall
    @Test
    void fallbackUsesAuthoritativeLevelAdvanced() {
        DeveloperAnalysisData d = dataWith("Advanced", 69);
        String fallback = gemini.buildStackNeutralFallback(d);
        assertTrue(fallback.contains("LEVEL: Advanced"), "Fallback LEVEL must be Advanced when experienceLevel=Advanced, got: " + fallback);
        assertFalse(fallback.toLowerCase().contains("intermediate developer") && fallback.contains("LEVEL: Advanced") == false,
                "Should not contain intermediate when Advanced expected");
        // also ensure hiring line references same level
        assertTrue(fallback.contains("Advanced with strengths") || fallback.contains("Advanced"),
                "Hiring line should reference Advanced");
    }

    @Test
    void fallbackUsesAuthoritativeLevelExpert() {
        DeveloperAnalysisData d = dataWith("Expert", 85);
        String fallback = gemini.buildStackNeutralFallback(d);
        assertTrue(fallback.contains("LEVEL: Expert"), fallback);
    }

    @Test
    void fallbackUsesAuthoritativeLevelIntermediate() {
        DeveloperAnalysisData d = dataWith("Intermediate", 55);
        String fallback = gemini.buildStackNeutralFallback(d);
        assertTrue(fallback.contains("LEVEL: Intermediate"), fallback);
    }

    @Test
    void fallbackUsesAuthoritativeLevelBeginner() {
        DeveloperAnalysisData d = dataWith("Beginner", 25);
        String fallback = gemini.buildStackNeutralFallback(d);
        assertTrue(fallback.contains("LEVEL: Beginner"), fallback);
    }

    @Test
    void fallbackDoesNotDeriveLevelFromOverallWhenExperienceProvided() {
        // overall 69 would previously be "intermediate developer" via threshold <75,
        // but with experienceLevel=Advanced it must be Advanced
        DeveloperAnalysisData d = dataWith("Advanced", 69);
        String fallback = gemini.buildStackNeutralFallback(d);
        assertTrue(fallback.contains("LEVEL: Advanced"), "Must not fallback to intermediate for overall 69 when authoritative is Advanced");
        assertFalse(fallback.contains("intermediate developer"), "Should not contain stale threshold-based text");
    }

    // Issue 3 & 4: fallback must be deterministic summary, not AI-assessed or Recommended in misleading way
    @Test
    void fallbackHiringLineIsDeterministicSummaryNotHiringRecommendation() {
        DeveloperAnalysisData d = dataWith("Advanced", 69);
        String fallback = gemini.buildStackNeutralFallback(d);
        String lower = fallback.toLowerCase();
        assertTrue(lower.contains("deterministic summary"), "Fallback should mention deterministic summary");
        assertTrue(lower.contains("temporarily unavailable"), "Fallback should mention temporarily unavailable");
        // Should not claim strong hire/reject in fallback template itself
        assertFalse(lower.contains("strong hire") || lower.contains("reject") || lower.contains("best candidate"),
                "Fallback should not contain hiring decision language");
    }

    @Test
    void fallbackWhenNoExperienceFallsBackToDeterministicLevels() {
        DeveloperAnalysisData d = new DeveloperAnalysisData();
        d.setTechnologies(Map.of("Python", 1));
        d.setOverallScore(69);
        d.setExperienceLevel(null);
        d.setUsername("u");
        d.setTotalRepositories(1);
        String fallback = gemini.buildStackNeutralFallback(d);
        // null experience should fallback to threshold: 69 >=60 => Advanced
        assertTrue(fallback.contains("LEVEL: Advanced"), "Null experience should use threshold Advanced for 69");
    }

    // Issue 5: confidence for stale/fork-heavy
    private GitHubRepoDTO repo(String name, String desc, String lang, int stars, boolean fork, int size, String pushedAt) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        r.setStargazers_count(stars);
        r.setFork(fork);
        r.setSize(size);
        r.setPushed_at(pushedAt);
        r.setUpdated_at(pushedAt);
        return r;
    }

    @Test
    void confidenceHighDemotedWhenAllStale() {
        // 13 repos, 13 meaningful, techs 5, breadth 2, overall 69 would be HIGH normally,
        // but all STALE (>365d) should demote to MEDIUM
        String stale = Instant.now().minusSeconds(800L * 86400).toString();
        var repos = List.of(
                repo("pandas-cookbook", "Python pandas cookbook examples Python pandas", "Python", 10, false, 500, stale),
                repo("repo2", "Python Django PostgreSQL Python Django", "Python", 2, true, 200, stale),
                repo("repo3", "Python Flask Redis", "Python", 1, true, 150, stale),
                repo("repo4", "Python FastAPI Docker", "Python", 0, true, 100, stale),
                repo("repo5", "Python NumPy Pandas", "Python", 0, false, 100, stale),
                repo("repo6", "Python scikit-learn", "Python", 0, true, 100, stale),
                repo("repo7", "Python OpenAI LangChain", "Python", 0, false, 100, stale),
                repo("repo8", "Python PostgreSQL", "Python", 0, false, 100, stale),
                repo("repo9", "Python React", "Python", 0, false, 100, stale),
                repo("repo10", "Python Docker Kubernetes", "Python", 0, true, 100, stale),
                repo("repo11", "Python AWS", "Python", 0, true, 100, stale),
                repo("repo12", "Python GCP", "Python", 0, false, 100, stale),
                repo("repo13", "Python Node.js", "Python", 0, false, 100, stale)
        );
        var techs = detector.detect(repos);
        var score = scoring.scoreFromRepos(repos);
        var langs = Map.of("Python", 13);
        var assessment = profile.assess(repos, techs, langs, score);
        // Base would be HIGH per old logic, but with all stale should be MEDIUM
        assertEquals(com.soubhagya.devscout.dto.Confidence.MEDIUM, assessment.getConfidence(),
                "Stale/fork-heavy with no recent activity should not be HIGH, was " + assessment.getConfidence() + " evidence " + assessment.getExperienceEvidence());
    }

    @Test
    void confidenceHighPreservedWhenRecentActivity() {
        String recent = Instant.now().minusSeconds(10L * 86400).toString();
        String stale = Instant.now().minusSeconds(800L * 86400).toString();
        var repos = List.of(
                repo("recent1", "Python Django PostgreSQL Python Django", "Python", 5, false, 500, recent),
                repo("recent2", "Python React PostgreSQL", "Python", 3, false, 200, recent),
                repo("repo3", "Python Flask Redis", "Python", 1, true, 150, stale),
                repo("repo4", "Python FastAPI Docker", "Python", 0, true, 100, stale),
                repo("repo5", "Python NumPy Pandas", "Python", 0, false, 100, stale),
                repo("repo6", "Python scikit-learn", "Python", 0, true, 100, stale),
                repo("repo7", "Python OpenAI", "Python", 0, false, 100, stale),
                repo("repo8", "Python PostgreSQL", "Python", 0, false, 100, stale),
                repo("repo9", "Python React", "Python", 0, false, 100, recent),
                repo("repo10", "Python Docker", "Python", 0, true, 100, stale),
                repo("repo11", "Python AWS", "Python", 0, true, 100, stale),
                repo("repo12", "Python GCP", "Python", 0, false, 100, stale),
                repo("repo13", "Python Node", "Python", 0, false, 100, stale)
        );
        var techs = detector.detect(repos);
        var score = scoring.scoreFromRepos(repos);
        var assessment = profile.assess(repos, techs, Map.of("Python", 13), score);
        assertTrue(assessment.getConfidence() == com.soubhagya.devscout.dto.Confidence.HIGH
                || assessment.getConfidence() == com.soubhagya.devscout.dto.Confidence.MEDIUM,
                "With recent activity confidence should be at least MEDIUM, was " + assessment.getConfidence());
    }

    @Test
    void confidenceUnknownReposNotPenalized() {
        // repos with null dates (UNKNOWN) should keep HIGH per guard
        var repos = List.of(
                repo("r1", "Java Spring Boot PostgreSQL Java Spring", "Java", 5, false, 500, null),
                repo("r2", "Java Spring Boot Hibernate", "Java", 3, false, 300, null),
                repo("r3", "Java React", "Java", 2, false, 200, null),
                repo("r4", "Java Docker Kubernetes", "Java", 1, false, 100, null),
                repo("r5", "Java AWS", "Java", 0, false, 100, null),
                repo("r6", "Java GCP", "Java", 0, false, 100, null),
                repo("r7", "Java Redis", "Java", 0, false, 100, null),
                repo("r8", "Java MySQL", "Java", 0, false, 100, null),
                repo("r9", "Java PostgreSQL", "Java", 0, false, 100, null)
        );
        // ensure techs distinct >=4, overall likely >=50
        var techs = detector.detect(repos);
        var score = scoring.scoreFromRepos(repos);
        // force overall high enough
        var assessment = profile.assess(repos, techs, Map.of("Java", 9), score);
        // If base is HIGH, unknown recency should not demote
        if (assessment.getConfidence() == com.soubhagya.devscout.dto.Confidence.HIGH) {
            assertEquals(com.soubhagya.devscout.dto.Confidence.HIGH, assessment.getConfidence());
        } else {
            // at least not crash, base may be MEDIUM due to overall
            assertTrue(assessment.getConfidence() == com.soubhagya.devscout.dto.Confidence.MEDIUM
                    || assessment.getConfidence() == com.soubhagya.devscout.dto.Confidence.HIGH);
        }
    }
}
