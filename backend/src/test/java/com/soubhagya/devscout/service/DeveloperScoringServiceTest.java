package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeveloperScoringServiceTest {

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

    private DeveloperScoreDTO scoreFromRepos(List<GitHubRepoDTO> repos) {
        Map<String,Integer> techs = detector.detect(repos);
        return scoring.score(techs);
    }

    @Test
    void test1_javaSpring() {
        List<GitHubRepoDTO> repos = List.of(
                repo("greencart", "Java Spring Boot Hibernate PostgreSQL", "Java")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getBackendScore() >= 60, "Backend should be strong, got " + s.getBackendScore());
        assertTrue(s.getDatabaseScore() >= 30, "Database meaningful, got " + s.getDatabaseScore());
        assertTrue(s.getOverallScore() >= 40, "Overall not collapsed");
        // backend should be > frontend when java-focused
        assertTrue(s.getBackendScore() > s.getFrontendScore());
    }

    @Test
    void test2_pythonDjango() {
        List<GitHubRepoDTO> repos = List.of(
                repo("pyapp", "Python Django PostgreSQL", "Python")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getBackendScore() >= 50, "Python+Django backend strong, got " + s.getBackendScore());
        assertTrue(s.getDatabaseScore() >= 30, "PostgreSQL database meaningful, got " + s.getDatabaseScore());
        assertTrue(s.getBackendScore() >= 50, "Must NOT collapse to low because Java absent");
    }

    @Test
    void test3_pythonFastAPI_withDocker() {
        List<GitHubRepoDTO> repos = List.of(
                repo("fastapi-svc", "Python FastAPI PostgreSQL Docker", "Python")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getBackendScore() >= 50, "FastAPI backend strong");
        assertTrue(s.getDatabaseScore() >= 30);
        Map<String,Integer> techs = detector.detect(repos);
        assertTrue(techs.containsKey("Docker"));
        assertTrue(techs.containsKey("FastAPI"));
    }

    @Test
    void test4_mern() {
        List<GitHubRepoDTO> repos = List.of(
                repo("mern-app", "JavaScript React Node.js Express MongoDB", "JavaScript")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getFrontendScore() >= 50, "Frontend strong, got " + s.getFrontendScore());
        assertTrue(s.getBackendScore() >= 30, "Backend strong via Node/Express, got " + s.getBackendScore());
        assertTrue(s.getDatabaseScore() >= 30, "MongoDB database, got " + s.getDatabaseScore());
    }

    @Test
    void test5_dotnet() {
        List<GitHubRepoDTO> repos = List.of(
                repo("dotnet-api", "C# ASP.NET Core SQL Server", "C#")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getBackendScore() >= 50, "C# backend strong, got " + s.getBackendScore());
        assertTrue(s.getDatabaseScore() >= 30, "SQL Server meaningful, got " + s.getDatabaseScore());
    }

    @Test
    void test6_go() {
        List<GitHubRepoDTO> repos = List.of(
                repo("go-service", "Go Gin PostgreSQL", "Go")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getBackendScore() >= 50, "Go backend strong, got " + s.getBackendScore());
        assertTrue(s.getDatabaseScore() >= 30);
    }

    @Test
    void test7_frontendSpecialist() {
        List<GitHubRepoDTO> repos = List.of(
                repo("design", "TypeScript React Next.js CSS", "TypeScript")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getFrontendScore() >= 60, "Frontend strong, got " + s.getFrontendScore());
        // Low backend must not automatically make overall terrible — specialization allowed
        // overall uses weighted, should stay >= 40 even with low backend
        assertTrue(s.getOverallScore() >= 35, "Overall not terrible despite low backend, got " + s.getOverallScore());
        // Ensure frontend > backend
        assertTrue(s.getFrontendScore() > s.getBackendScore());
    }

    @Test
    void test8_aiDataSpecialist() {
        List<GitHubRepoDTO> repos = List.of(
                repo("ml-pipeline", "Python PyTorch Pandas scikit-learn", "Python")
        );
        DeveloperScoreDTO s = scoreFromRepos(repos);
        assertTrue(s.getAiScore() >= 60, "AI strong, got " + s.getAiScore());
        // Python alone check: Python without AI libs should NOT be strong AI
        Map<String,Integer> pyOnly = detector.detect(List.of(repo("script","Python scripting","Python")));
        DeveloperScoreDTO pyScore = scoring.score(pyOnly);
        assertTrue(pyScore.getAiScore() < 50, "Python alone must not be strong AI, got " + pyScore.getAiScore());
    }

    @Test
    void capAt100() {
        // Many repos with many signals should cap at 100, not exceed
        GitHubRepoDTO r1 = repo("r1","Java Spring Boot Hibernate JPA", "Java");
        GitHubRepoDTO r2 = repo("r2","Java Spring Boot Hibernate", "Java");
        GitHubRepoDTO r3 = repo("r3","Java Spring Boot", "Java");
        GitHubRepoDTO r4 = repo("r4","Java Spring Boot", "Java");
        GitHubRepoDTO r5 = repo("r5","Java Spring Boot", "Java");
        GitHubRepoDTO r6 = repo("r6","Java Spring Boot", "Java");
        GitHubRepoDTO r7 = repo("r7","Java Spring Boot", "Java");
        DeveloperScoreDTO s = scoreFromRepos(List.of(r1,r2,r3,r4,r5,r6,r7));
        assertTrue(s.getBackendScore() <= 100);
        assertEquals(100, s.getBackendScore());
    }

    @Test
    void emptyProfileScoresAtBase() {
        DeveloperScoreDTO s = scoring.score(Map.of());
        assertEquals(20, s.getBackendScore());
        assertEquals(20, s.getFrontendScore());
        assertEquals(20, s.getDatabaseScore());
        assertEquals(20, s.getAiScore());
        assertEquals(20, s.getOverallScore());
    }

    @Test
    void specializationOverallNotHarsh() {
        // Backend specialist: 92 backend, 25 frontend, 82 database, 15 ai -> overall should not be simple mean 53
        int overall = scoring.computeOverallForTest(92,25,82,15);
        int mean = (92+25+82+15)/4;
        assertTrue(overall > mean, "Weighted overall should be greater than mean for specialist, got overall "+overall+" mean "+mean);
        assertTrue(overall >= 60 && overall <= 85, "Overall should be reasonable, got "+overall);
    }

    @Test
    void overallNotJustMax() {
        int overall = scoring.computeOverallForTest(90,20,20,20);
        assertTrue(overall < 90, "Overall must not be just max, got "+overall);
        assertTrue(overall > 40, "Overall must not collapse completely for single specialist");
    }

    @Test
    void experienceLevel_logic() {
        assertEquals("Beginner", scoring.experienceLevel(20,0));
        assertEquals("Beginner", scoring.experienceLevel(85,1));
        assertEquals("Intermediate", scoring.experienceLevel(85,2));
        assertEquals("Advanced", scoring.experienceLevel(85,3));
        assertEquals("Expert", scoring.experienceLevel(85,6,10));
        assertEquals("Advanced", scoring.experienceLevel(65,3));
        assertEquals("Intermediate", scoring.experienceLevel(45,2));
    }
}
