package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fairness / regression test: ensure Java-bias does not return.
 * Comparable backend evidence across ecosystems must produce comparable Backend scores.
 */
class FairnessRegressionTest {

    private TechnologyDetector detector;
    private DeveloperScoringService scoring;

    @BeforeEach
    void setUp() {
        detector = new TechnologyDetector();
        scoring = new DeveloperScoringService(detector);
    }

    private GitHubRepoDTO repo(String name, String desc, String lang) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        return r;
    }

    private int backendScoreFor(String desc, String lang) {
        Map<String,Integer> techs = detector.detect(List.of(repo("r", desc, lang)));
        return scoring.score(techs).getBackendScore();
    }

    private int dbScoreFor(String desc) {
        Map<String,Integer> techs = detector.detect(List.of(repo("r", desc, null)));
        return scoring.score(techs).getDatabaseScore();
    }

    @Test
    void backendFairness_javaPythonNodeDotNetComparable() {
        int javaScore = backendScoreFor("Java Spring Boot", "Java");
        int pythonScore = backendScoreFor("Python Django", "Python");
        int nodeScore = backendScoreFor("Node.js Express", "JavaScript");
        int dotnetScore = backendScoreFor("C# ASP.NET Core", "C#");
        int goScore = backendScoreFor("Go Gin", "Go");
        int phpScore = backendScoreFor("PHP Laravel", "PHP");

        // All should be reasonably comparable (within 15 points) because each has 2 backend signals
        int[] scores = {javaScore, pythonScore, nodeScore, dotnetScore, goScore, phpScore};
        for (int s : scores) {
            assertTrue(s >= 35, "Each backend stack should be at least base+signals, got "+s);
        }
        int max = max(scores);
        int min = min(scores);
        assertTrue(max - min <= 15, "Backend scores should be within 15 across stacks, max="+max+" min="+min+" scores java="+javaScore+" py="+pythonScore+" node="+nodeScore+" dotnet="+dotnetScore+" go="+goScore+" php="+phpScore);
    }

    @Test
    void databaseFairness_allDbEquallyRecognized() {
        int pg = dbScoreFor("PostgreSQL");
        int my = dbScoreFor("MySQL");
        int mongo = dbScoreFor("MongoDB");
        int sqlserver = dbScoreFor("SQL Server");
        int redis = dbScoreFor("Redis");
        int sqlite = dbScoreFor("SQLite");

        // Each single DB should give same boost (20+15=35)
        assertEquals(35, pg);
        assertEquals(35, my);
        assertEquals(35, mongo);
        assertEquals(35, sqlserver);
        assertEquals(35, redis);
        assertEquals(35, sqlite);

        // Two DBs should give same as each other
        int pgMy = scoring.score(detector.detect(List.of(repo("r","PostgreSQL MySQL",null)))).getDatabaseScore();
        int mongoRedis = scoring.score(detector.detect(List.of(repo("r","MongoDB Redis",null)))).getDatabaseScore();
        assertEquals(pgMy, mongoRedis);
        assertEquals(50, pgMy); // 20+2*15=50
    }

    @Test
    void noPrestige_biasNotHardCoded() {
        // Directly craft tech maps to ensure scoring does not hardcode Java prestige
        // Simulate same counts but different techs
        Map<String,Integer> javaMap = Map.of("Java",1, "Spring Boot",1);
        Map<String,Integer> pythonMap = Map.of("Python",1, "Django",1);
        Map<String,Integer> nodeMap = Map.of("Node.js",1, "Express",1);
        int j = scoring.score(javaMap).getBackendScore();
        int p = scoring.score(pythonMap).getBackendScore();
        int n = scoring.score(nodeMap).getBackendScore();
        assertEquals(j, p, "Java and Python same count same score");
        assertEquals(p, n, "Python and Node same count same score");
    }

    @Test
    void unknownTechnologiesNotPenalized() {
        Map<String,Integer> unknown = detector.detect(List.of(repo("r","Rust Elixir Kotlin project","Rust")));
        DeveloperScoreDTO s = scoring.score(unknown);
        // Unknown tech should not cause negative or exception, stays at base
        assertEquals(20, s.getBackendScore());
        assertEquals(20, s.getOverallScore());
        // Ensure no exception on random text
        Map<String,Integer> random = detector.detect(List.of(repo("r","foobar blabla",null)));
        DeveloperScoreDTO s2 = scoring.score(random);
        assertEquals(20, s2.getBackendScore());
    }

    @Test
    void singleRepoVarietyStillFair() {
        // Single repo with Java+Spring Boot (2 signals) vs Python+FastAPI (2 signals) vs Node+Express (2)
        int java = backendScoreFor("Java Spring Boot", "Java");
        int python = backendScoreFor("Python FastAPI", "Python");
        int node = backendScoreFor("Node.js Express", "JavaScript");
        // Allow minor difference but within 10
        assertTrue(Math.abs(java - python) <= 10, "Java vs Python FastAPI gap "+Math.abs(java-python));
        assertTrue(Math.abs(python - node) <= 10);
    }

    private int max(int[] a){int m=a[0]; for(int v:a) m=Math.max(m,v); return m;}
    private int min(int[] a){int m=a[0]; for(int v:a) m=Math.min(m,v); return m;}
}
