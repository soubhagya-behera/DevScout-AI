package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.service.GeminiService;
import com.soubhagya.devscout.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase10_1PerRepoCapTest {

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

    private GitHubRepoDTO repo(String name, String desc, String lang, int size, String pushedAt) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        r.setSize(size);
        r.setPushed_at(pushedAt);
        r.setUpdated_at(pushedAt);
        r.setStargazers_count(0);
        return r;
    }
    private GitHubRepoDTO repo(String name, String desc, String lang, int size) {
        return repo(name, desc, lang, size, Instant.now().toString());
    }

    @Test
    void oneRepoJavaSpringJpaCountsOnceForBackend() {
        var r = repo("svc", "Java Spring Boot JPA", "Java", 200);
        var perRepo = detector.detectStrengthPerRepo(r);
        long backendTechs = perRepo.entrySet().stream().filter(e -> detector.capabilityForDisplay(e.getKey()) == TechnologyDetector.Capability.BACKEND).count();
        assertTrue(backendTechs >= 2, "Repo should detect at least 2 backend techs, was " + perRepo);
        var score = scoring.scoreFromRepos(List.of(r));
        // With per-repo max, 1 repo should give backend ~28-30, not 3* signals
        // 3 techs with old per-signal would be 3*0.65=1.95 -> 49, with per-repo max should be ~30
        assertTrue(score.getBackendScore() < 45, "One repo with 3 backend techs must not give 3 signals, was " + score.getBackendScore());
        assertTrue(score.getBackendScore() >= 25, "Should still show backend evidence");
        // Verify scoring used per-repo max by checking effective sum is 0.65*quality not sum
        double q = scoring.qualityFactor(r);
        double expectedMax = 0.65 * q; // MEDIUM Spring Boot is max (Java also 0.65)
        // effective with one entry = max, score should be 20+round(max*15)
        int expected = 20 + (int) Math.round(expectedMax * 15);
        assertEquals(expected, score.getBackendScore(), "Per-repo max score should match max weight");
    }

    @Test
    void oneRepoReactJsHtmlCountsOnceForFrontend() {
        var r = repo("ui", "React JavaScript HTML", "JavaScript", 200);
        var score = scoring.scoreFromRepos(List.of(r));
        assertTrue(score.getFrontendScore() < 45, "One repo with 3 frontend techs must be capped to one, was " + score.getFrontendScore());
        assertTrue(score.getFrontendScore() >= 25);
    }

    @Test
    void oneRepoCanContributeToBackendAndFrontendIndependently() {
        var r = repo("full", "Java Spring Boot React", "Java", 300);
        r.setPushed_at(Instant.now().toString());
        var score = scoring.scoreFromRepos(List.of(r));
        // Should have both backend and frontend from same repo
        assertTrue(score.getBackendScore() >= 25, "Backend from same repo");
        assertTrue(score.getFrontendScore() >= 25, "Frontend from same repo");
        // But each axis only once, so not huge
        assertTrue(score.getBackendScore() < 50 && score.getFrontendScore() < 50);
    }

    @Test
    void strongBeatsMediumWithinSameRepo() {
        var strongRepo = repo("s", "Spring Boot", "Java", 200);
        strongRepo.setTopics(List.of("spring-boot"));
        strongRepo.setDependencyEvidence("spring-boot-starter");
        var mediumRepo = repo("m", "Spring Boot", "Java", 200);
        // medium via description only
        var strongScore = scoring.scoreFromRepos(List.of(strongRepo));
        var mediumScore = scoring.scoreFromRepos(List.of(mediumRepo));
        assertTrue(strongScore.getBackendScore() > mediumScore.getBackendScore(),
                "Strong should beat medium: strong " + strongScore.getBackendScore() + " medium " + mediumScore.getBackendScore());
    }

    @Test
    void qualityAppliedOncePerRepo() {
        var substantial = repo("sub", "Java Spring Boot", "Java", 500);
        substantial.setStargazers_count(10);
        substantial.setPushed_at(Instant.now().toString());
        var shallow = repo("shallow", "Java Spring Boot", "Java", 10);
        shallow.setFork(true);
        shallow.setPushed_at(Instant.now().minusSeconds(800L*86400).toString());
        var scSub = scoring.scoreFromRepos(List.of(substantial));
        var scFork = scoring.scoreFromRepos(List.of(shallow));
        assertTrue(scSub.getBackendScore() > scFork.getBackendScore(),
                "Quality: substantial recent should beat shallow stale fork");
        // Quality factor check: substantial q ~1.0, shallow q ~0.35
        double qSub = scoring.qualityFactor(substantial);
        double qFork = scoring.qualityFactor(shallow);
        assertTrue(qSub > qFork);
    }

    @Test
    void diminishingBasedOnRepoContributionsNotTechCount() {
        // 12 tech signals across 4 repos (3 techs each, per-repo max collapses to 4) vs 12 across 12 repos
        List<GitHubRepoDTO> fourRepos = new ArrayList<>();
        for (int i=0;i<4;i++) fourRepos.add(repo("r"+i, "Java Spring Boot JPA", "Java", 200));
        List<GitHubRepoDTO> twelveRepos = new ArrayList<>();
        for (int i=0;i<12;i++) twelveRepos.add(repo("r"+i, "Java", "Java", 200));
        var s4 = scoring.scoreFromRepos(fourRepos);
        var s12 = scoring.scoreFromRepos(twelveRepos);
        // 12 repos should score higher than 4 repos, but not 3x
        assertTrue(s12.getBackendScore() > s4.getBackendScore());
        assertTrue(s12.getBackendScore() < 100, "Even 12 repos should not saturate");
        // 4 repos with 3 techs each per-signal would be 12 signals -> old would be 100, new max per-repo 4 -> 54
        assertTrue(s4.getBackendScore() < 70, "4 repos collapsed must not saturate, was " + s4.getBackendScore());
    }

    @Test
    void devGoyalBroadProfileNotSaturated() {
        // Reconstruct 18 repos as per observed fixture but with per-repo max
        List<GitHubRepoDTO> repos = new ArrayList<>();
        for (int i=0;i<8;i++) repos.add(repo("spring"+i, "Java Spring Boot", "Java", 200));
        for (int i=0;i<8;i++) repos.add(repo("react"+i, "React JavaScript", "JavaScript", 150));
        for (int i=0;i<2;i++) repos.add(repo("mysql"+i, "MySQL", "Java", 100));
        // 18 total
        repos = repos.subList(0, 18);
        var score = scoring.scoreFromRepos(repos);
        assertTrue(score.getBackendScore() < 100, "Backend not 100, was " + score.getBackendScore());
        assertTrue(score.getFrontendScore() < 100, "Frontend not 100");
        assertTrue(score.getAiScore() < 100, "AI not 100 with only Python/Go etc");
        assertTrue(score.getOverallScore() < 90);
    }

    @Test
    void threeTechsOneRepoVsThreeReposSpread() {
        // 3 techs in one repo should be 1 contribution vs 3 repos each with 1 tech = 3 contributions
        var oneRepoThreeTechs = List.of(repo("r", "Java Spring Boot JPA", "Java", 200));
        var threeReposOneTech = List.of(repo("r1", "Java", "Java", 200), repo("r2", "Spring Boot", "Java", 200), repo("r3", "JPA", "Java", 200));
        var sOne = scoring.scoreFromRepos(oneRepoThreeTechs);
        var sThree = scoring.scoreFromRepos(threeReposOneTech);
        assertTrue(sThree.getBackendScore() > sOne.getBackendScore(),
                "3 repos with 1 tech each should score higher than 1 repo with 3 techs collapsed: one " + sOne.getBackendScore() + " three " + sThree.getBackendScore());
        assertTrue(sOne.getBackendScore() < 40, "One repo with 3 techs must be capped to one, was " + sOne.getBackendScore());
    }

    @Test
    void crossStackFairnessPerRepo() {
        var javaRepos = List.of(repo("j1","Java Spring Boot","Java",200), repo("j2","Java Spring Boot","Java",200));
        var pyRepos = List.of(repo("p1","Python Django","Python",200), repo("p2","Python Django","Python",200));
        var nodeRepos = List.of(repo("n1","Node.js Express","JavaScript",200), repo("n2","Node.js Express","JavaScript",200));
        var goRepos = List.of(repo("g1","Go Gin","Go",200), repo("g2","Go Gin","Go",200));
        var sJava = scoring.scoreFromRepos(javaRepos).getBackendScore();
        var sPy = scoring.scoreFromRepos(pyRepos).getBackendScore();
        var sNode = scoring.scoreFromRepos(nodeRepos).getBackendScore();
        var sGo = scoring.scoreFromRepos(goRepos).getBackendScore();
        int max = Math.max(Math.max(sJava,sPy), Math.max(sNode,sGo));
        int min = Math.min(Math.min(sJava,sPy), Math.min(sNode,sGo));
        assertTrue(max-min <= 20, "Stack fairness within 20, max "+max+" min "+min);
    }

    @Test
    void regressionLivePathUsesDeveloperScoringService() throws Exception {
        // Mock GitHubService to use controlled repos and verify scoringService is sole source
        var repos = List.of(repo("a","Java Spring Boot","Java",200), repo("b","React","JavaScript",150));
        GitHubService spy = Mockito.spy(new GitHubService(gemini, detector, scoring, profile) {
            @Override public List<GitHubRepoDTO> getRepositories(String u) { return new ArrayList<>(repos); }
            @Override String fetchReadme(String o, String r) { return null; }
            @Override String fetchManifestEvidenceWithBudget(String o, String r, int[] c) { return null; }
        });
        // Also spy scoring to verify called
        DeveloperScoringService scoringSpy = Mockito.spy(scoring);
        // Need to inject scoringSpy into spy GitHubService via reflection
        var field = GitHubService.class.getDeclaredField("scoringService");
        field.setAccessible(true);
        field.set(spy, scoringSpy);
        spy.clearCacheForTests();
        var report = spy.getOrGenerateReport("testLivePath");
        // Verify per-repo max diminishing score, not linear old score
        // For 1 backend +1 frontend, old linear with 2 techs would be 2*15+20=50, new per-repo max with 2 repos: backend 1, frontend 1 => each 30, overall ~30
        // Check that scores are from scoringSpy
        Mockito.verify(scoringSpy, atLeastOnce()).scoreFromRepos(anyList());
        // Ensure not using old linear score(Map) path for report (should be scoreFromRepos)
        // The report's backend should be 30 not 50 if linear
        assertTrue(report.getBackendScore() < 50, "Live path must use per-repo diminishing, backend was " + report.getBackendScore());
        assertTrue(report.getFrontendScore() < 50);
    }
}
