package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperProfileServiceTest {

    private TechnologyDetector detector;
    private DeveloperScoringService scoring;
    private DeveloperProfileService profile;

    @BeforeEach
    void setUp() {
        detector = new TechnologyDetector();
        scoring = new DeveloperScoringService(detector);
        profile = new DeveloperProfileService(detector);
    }

    private GitHubRepoDTO repo(String name, String desc, String lang, int stars) {
        GitHubRepoDTO r = new GitHubRepoDTO();
        r.setName(name);
        r.setDescription(desc);
        r.setLanguage(lang);
        r.setStargazers_count(stars);
        return r;
    }
    private GitHubRepoDTO repo(String name, String desc, String lang) { return repo(name, desc, lang, 0); }

    private DeveloperProfileAssessment assess(List<GitHubRepoDTO> repos) {
        Map<String,Integer> techs = detector.detect(repos);
        Map<String,Integer> langs = new HashMap<>();
        for (GitHubRepoDTO r : repos) if (r.getLanguage()!=null) langs.put(r.getLanguage(), langs.getOrDefault(r.getLanguage(),0)+1);
        DeveloperScoreDTO scores = scoring.score(techs);
        return profile.assess(repos, techs, langs, scores);
    }

    @Test
    void test1_javaSpringPostgresBackend() {
        var repos = List.of(repo("svc","Java Spring Boot PostgreSQL","Java"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, a.getProfileType());
        assertTrue(a.getCapabilitySignals().get("BACKEND") >=1);
        assertEquals("BACKEND", a.getSpecialization());
    }

    @Test
    void test2_pythonDjangoBackend() {
        var repos = List.of(repo("py","Python Django PostgreSQL","Python"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, a.getProfileType());
        assertTrue(a.getDepth() >= 35);
    }

    @Test
    void test3_pythonFastAPI() {
        var repos = List.of(repo("api","Python FastAPI PostgreSQL Docker","Python"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, a.getProfileType());
    }

    @Test
    void test4_mernFullStack() {
        var repos = List.of(
                repo("frontend","React JavaScript","JavaScript"),
                repo("backend","Node.js Express MongoDB","JavaScript"),
                repo("full","React Node.js MongoDB","JavaScript",5)
        );
        var a = assess(repos);
        assertEquals(DeveloperProfileType.FULL_STACK_DEVELOPER, a.getProfileType());
        assertTrue(a.getBreadth() >=2, "MERN should have breadth >=2, got "+a.getBreadth());
    }

    @Test
    void test5_frontendFocused() {
        var repos = List.of(repo("ui","TypeScript React Next.js CSS","TypeScript"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.FRONTEND_DEVELOPER, a.getProfileType());
        assertEquals("FRONTEND", a.getSpecialization());
    }

    @Test
    void test6_pythonTensorFlowAI() {
        var repos = List.of(repo("ml","Python PyTorch TensorFlow","Python"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.AI_ML_DEVELOPER, a.getProfileType());
    }

    @Test
    void test7_pythonDataOriented() {
        var repos = List.of(repo("data","Python Pandas NumPy scikit-learn","Python"));
        var a = assess(repos);
        // data-oriented should be DATA_BACKEND or AI_ML, not BACKEND generic
        assertTrue(a.getProfileType()==DeveloperProfileType.DATA_BACKEND_DEVELOPER || a.getProfileType()==DeveloperProfileType.AI_ML_DEVELOPER,
                "Expected data/backend or AI, got "+a.getProfileType());
    }

    @Test
    void test8_dotnetBackend() {
        var repos = List.of(repo("api","C# ASP.NET Core SQL Server","C#"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, a.getProfileType());
    }

    @Test
    void test9_goGinBackend() {
        var repos = List.of(repo("svc","Go Gin PostgreSQL","Go"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, a.getProfileType());
    }

    @Test
    void test10_javaFullStack() {
        var repos = List.of(
                repo("backend","Java Spring Boot PostgreSQL","Java"),
                repo("frontend","React JavaScript","JavaScript")
        );
        var a = assess(repos);
        assertEquals(DeveloperProfileType.FULL_STACK_DEVELOPER, a.getProfileType());
    }

    @Test
    void test11_sparseFewRepos() {
        var repos = List.of(repo("one","Java Spring Boot","Java"));
        var a = assess(repos);
        assertEquals(Confidence.LOW, a.getConfidence(), "Single repo should be LOW confidence");
        // experience should not be expert
        assertNotEquals("Expert", a.getExperienceLevel());
    }

    @Test
    void test12_manyReposWeakEvidence() {
        List<GitHubRepoDTO> repos = new ArrayList<>();
        for (int i=0;i<15;i++) repos.add(repo("r"+i, "test repo", null));
        var a = assess(repos);
        // many repos but no tech => meaningful low => LOW confidence and Beginner
        assertEquals(Confidence.LOW, a.getConfidence());
        assertEquals("Beginner", a.getExperienceLevel());
        assertTrue(a.getMeaningfulRepositories() < 3);
    }

    @Test
    void test13_specializedBackend() {
        var repos = List.of(
                repo("a","Java Spring Boot PostgreSQL","Java"),
                repo("b","Java Spring Boot PostgreSQL","Java"),
                repo("c","Java Spring Boot Hibernate","Java")
        );
        var a = assess(repos);
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, a.getProfileType());
        assertEquals("BACKEND", a.getSpecialization());
        assertTrue(a.getDepth() >= 60);
        assertTrue(a.getBreadth() <= 3); // specialized not broad
    }

    @Test
    void test14_broadFullStack() {
        var repos = List.of(
                repo("b1","Java Spring Boot PostgreSQL","Java"),
                repo("b2","Python FastAPI Redis","Python"),
                repo("f1","React TypeScript Next.js","TypeScript"),
                repo("ai","Python TensorFlow","Python", 10)
        );
        var a = assess(repos);
        assertTrue(a.getBreadth() >=3, "Broad should have breadth >=3, got "+a.getBreadth());
        // breadth 3+ suggests full-stack or general, not single specialization
    }

    @Test
    void test15_unknownTechnologies() {
        var repos = List.of(repo("r","Rust Elixir Kotlin project","Rust"));
        var a = assess(repos);
        assertEquals(DeveloperProfileType.UNKNOWN, a.getProfileType());
        assertEquals(Confidence.LOW, a.getConfidence());
    }

    @Test
    void test16_noRepositories() {
        var a = assess(List.of());
        assertEquals(DeveloperProfileType.UNKNOWN, a.getProfileType());
        assertEquals(Confidence.LOW, a.getConfidence());
        assertEquals("Beginner", a.getExperienceLevel());
        assertEquals(0, a.getMeaningfulRepositories());
    }

    @Test
    void test17_starsButWeakTech() {
        var repos = List.of(
                repo("popular","my awesome project", null, 100),
                repo("popular2","another repo", null, 50)
        );
        var a = assess(repos);
        // stars without tech should not give high experience
        assertNotEquals("Expert", a.getExperienceLevel());
        assertNotEquals("Advanced", a.getExperienceLevel());
        assertEquals(Confidence.LOW, a.getConfidence());
    }

    @Test
    void test18_strongTechLowStars() {
        var repos = List.of(
                repo("svc1","Java Spring Boot PostgreSQL","Java",0),
                repo("svc2","Python FastAPI PostgreSQL","Python",0),
                repo("svc3","React Node.js PostgreSQL","JavaScript",0),
                repo("svc4","Go Gin PostgreSQL","Go",0),
                repo("svc5","Java Spring Boot Redis","Java",0),
                repo("svc6","Python Django PostgreSQL","Python",0),
                repo("svc7","React TypeScript","TypeScript",0),
                repo("svc8","Java Spring Boot","Java",0)
        );
        var a = assess(repos);
        // strong tech but 0 stars should still be at least Intermediate/Advanced, not penalized to Beginner
        assertNotEquals("Beginner", a.getExperienceLevel(), "Strong tech low stars should not be Beginner");
        assertTrue(a.getConfidence()==Confidence.MEDIUM || a.getConfidence()==Confidence.HIGH);
    }

    @Test
    void unrelatedTechShouldNotChangeProfile() {
        var base = List.of(repo("svc","Java Spring Boot PostgreSQL","Java"));
        var withUnrelated = List.of(
                repo("svc","Java Spring Boot PostgreSQL","Java"),
                repo("extra","Rust random", "Rust")
        );
        var a1 = assess(base);
        var a2 = assess(withUnrelated);
        assertEquals(a1.getProfileType(), a2.getProfileType(), "Unrelated Rust should not change Java backend profile");
    }

    @Test
    void pythonBackendVsAI_distinction() {
        var backend = assess(List.of(repo("api","Python Django PostgreSQL","Python")));
        var ai = assess(List.of(repo("ml","Python PyTorch TensorFlow","Python")));
        assertEquals(DeveloperProfileType.BACKEND_DEVELOPER, backend.getProfileType());
        assertEquals(DeveloperProfileType.AI_ML_DEVELOPER, ai.getProfileType());
    }

    @Test
    void confidenceScalesWithEvidence() {
        var sparse = assess(List.of(repo("a","Java Spring Boot","Java")));
        List<GitHubRepoDTO> many = new ArrayList<>();
        for (int i=0;i<10;i++) many.add(repo("r"+i, "Java Spring Boot PostgreSQL React","Java",2));
        var rich = assess(many);
        assertTrue(rich.getConfidence().ordinal() > sparse.getConfidence().ordinal(),
                "Rich profile should have higher confidence");
    }

    @Test
    void experienceNotDominatedByStars() {
        var lowStars = assess(List.of(
                repo("a","Java Spring Boot PostgreSQL","Java",0),
                repo("b","Java Spring Boot PostgreSQL","Java",0),
                repo("c","Java Spring Boot PostgreSQL","Java",0),
                repo("d","Java Spring Boot PostgreSQL","Java",0)
        ));
        var highStarsWeakTech = assess(List.of(
                repo("x","test",null,100),
                repo("y","demo",null,100),
                repo("z","sample",null,100),
                repo("w","hello",null,100)
        ));
        assertTrue(lowStars.getExperienceLevel().equals("Intermediate") || lowStars.getExperienceLevel().equals("Advanced"),
                "Low stars but strong tech should not be Beginner, got "+lowStars.getExperienceLevel());
        assertEquals("Beginner", highStarsWeakTech.getExperienceLevel());
    }
}
