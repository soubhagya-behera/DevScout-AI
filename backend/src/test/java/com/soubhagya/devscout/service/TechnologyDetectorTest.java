package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TechnologyDetectorTest {

    private TechnologyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TechnologyDetector();
    }

    private GitHubRepoDTO repo(String name, String desc, String lang) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        return r;
    }

    @Test
    void detectJavaStack_fromDescriptionAndLanguage() {
        GitHubRepoDTO r = repo("greencart", "Full Stack with Java Spring Boot Hibernate PostgreSQL", "Java");
        Map<String,Integer> techs = detector.detect(List.of(r));
        assertTrue(techs.containsKey("Java"), "Java from language");
        assertTrue(techs.containsKey("Spring Boot"));
        assertTrue(techs.containsKey("Hibernate"));
        assertTrue(techs.containsKey("PostgreSQL"));
        // No false double count for spring generic
        assertFalse(techs.containsKey("Spring") && techs.get("Spring Boot")==1 && techs.containsKey("Spring"), "Spring generic should be removed when Spring Boot present");
    }

    @Test
    void detectMern() {
        GitHubRepoDTO r = repo("mern-shop", "JavaScript React Node.js Express MongoDB", "JavaScript");
        Map<String,Integer> techs = detector.detect(List.of(r));
        assertTrue(techs.containsKey("React"));
        assertTrue(techs.containsKey("Node.js"));
        assertTrue(techs.containsKey("Express"));
        assertTrue(techs.containsKey("MongoDB"));
        assertTrue(techs.containsKey("JavaScript"));
    }

    @Test
    void normalizePostgresAliases() {
        GitHubRepoDTO r1 = repo("db1", "postgres db", null);
        GitHubRepoDTO r2 = repo("db2", "PostgreSQL", null);
        GitHubRepoDTO r3 = repo("db3", "psql setup", null);
        Map<String,Integer> m1 = detector.detect(List.of(r1));
        Map<String,Integer> m2 = detector.detect(List.of(r2));
        Map<String,Integer> m3 = detector.detect(List.of(r3));
        assertTrue(m1.containsKey("PostgreSQL"));
        assertTrue(m2.containsKey("PostgreSQL"));
        assertTrue(m3.containsKey("PostgreSQL"));
    }

    @Test
    void normalizeNodeAliases() {
        assertTrue(detector.detect(List.of(repo("a","node.js service",null))).containsKey("Node.js"));
        assertTrue(detector.detect(List.of(repo("b","nodejs api",null))).containsKey("Node.js"));
        assertTrue(detector.detect(List.of(repo("c","node service",null))).containsKey("Node.js"));
    }

    @Test
    void normalizeReactAliases() {
        assertTrue(detector.detect(List.of(repo("a","react app",null))).containsKey("React"));
        assertTrue(detector.detect(List.of(repo("b","react.js app",null))).containsKey("React"));
    }

    @Test
    void normalizeDotNetAliases() {
        assertTrue(detector.detect(List.of(repo("a","asp.net core api",null))).containsKey("ASP.NET Core"));
        assertTrue(detector.detect(List.of(repo("b","dotnet service","C#"))).containsKey(".NET") || detector.detect(List.of(repo("b","dotnet service","C#"))).containsKey("C#"));
    }

    @Test
    void dedupPerRepo_springBootNotDoubleCounted() {
        GitHubRepoDTO r = repo("app", "Spring Boot Spring Boot spring boot", null);
        Map<String,Integer> techs = detector.detect(List.of(r));
        assertEquals(1, techs.getOrDefault("Spring Boot",0));
    }

    @Test
    void wordBoundary_javaNotMatchedInJavascript() {
        GitHubRepoDTO r = repo("frontend", "javascript project", "JavaScript");
        Map<String,Integer> techs = detector.detect(List.of(r));
        // Should have JavaScript but NOT Java via text "javascript"
        assertTrue(techs.containsKey("JavaScript"));
        // Java should only come from language==Java, not from javascript text; this repo lang is JavaScript so no Java
        assertFalse(techs.containsKey("Java"), "javascript text must not trigger java detection");
    }

    @Test
    void wordBoundary_goNotMatchedInDjango() {
        GitHubRepoDTO r = repo("api","django rest framework", null);
        Map<String,Integer> techs = detector.detect(List.of(r));
        assertTrue(techs.containsKey("Django"));
        assertFalse(techs.containsKey("Go"), "django should not trigger go via substring");
    }

    @Test
    void languageFallback_detectsPythonAndGo() {
        Map<String,Integer> py = detector.detect(List.of(repo("p","no desc","Python")));
        assertTrue(py.containsKey("Python"));
        Map<String,Integer> go = detector.detect(List.of(repo("g","microservice","Go")));
        assertTrue(go.containsKey("Go"));
        Map<String,Integer> cs = detector.detect(List.of(repo("c","api","C#")));
        assertTrue(cs.containsKey("C#"));
    }

    @Test
    void countsOccurrencesAcrossRepos() {
        GitHubRepoDTO r1 = repo("r1","React frontend", "JavaScript");
        GitHubRepoDTO r2 = repo("r2","React dashboard", "JavaScript");
        Map<String,Integer> techs = detector.detect(List.of(r1,r2));
        assertEquals(2, techs.get("React"));
    }

    @Test
    void unknownTechGracefullyIgnored() {
        Map<String,Integer> techs = detector.detect(List.of(repo("r","Rust Elixir Kotlin project", "Rust")));
        // Rust language not mapped -> no crash, techMap not contain those
        assertFalse(techs.containsKey("Rust"));
        assertTrue(techs.isEmpty() || techs.size()>=0);
    }

    @Test
    void detectsNamePlusDescription() {
        GitHubRepoDTO r = repo("docker-compose-example", null, null);
        Map<String,Integer> techs = detector.detect(List.of(r));
        assertTrue(techs.containsKey("Docker"));
    }

    @Test
    void nullReposHandled() {
        assertTrue(detector.detect(null).isEmpty());
        assertTrue(detector.detect(List.of()).isEmpty());
    }
}
