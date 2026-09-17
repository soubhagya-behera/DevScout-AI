package com.soubhagya.devscout.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soubhagya.devscout.dto.*;
import com.soubhagya.devscout.exception.*;
import com.soubhagya.devscout.service.GeminiService;
import com.soubhagya.devscout.service.GitHubService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GitHubController.class)
class ReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubService gitHubService;

    @MockitoBean
    private GeminiService geminiService;

    private FinalReportDTO sampleReport(String username) {
        FinalReportDTO dto = new FinalReportDTO();
        dto.setUsername(username);
        dto.setOverallScore(75);
        dto.setBackendScore(80);
        dto.setFrontendScore(60);
        dto.setDatabaseScore(50);
        dto.setAiScore(20);
        dto.setTechnologies(Map.of("Spring Boot", 2, "React", 1));
        dto.setAiAnalysis("LEVEL: Advanced\nTOP_STRENGTHS:\n- Spring\nIMPROVEMENTS:\n- Test\nHIRING_RECOMMENDATION:\nHire");
        dto.setProfileType("Backend Developer");
        dto.setConfidence("MEDIUM");
        dto.setSpecialization("BACKEND");
        dto.setExperienceLevel("Intermediate");
        dto.setExperienceEvidence("overall=75 meaningful=3/5");
        dto.setMeaningfulRepositories(3);
        dto.setTotalRepositories(5);
        dto.setTotalStars(10);
        dto.setBreadth(2);
        dto.setDepth(80);
        dto.setDistinctTechnologies(3);
        dto.setCapabilitySignals(Map.of("BACKEND", 2));
        dto.setEvidenceSummary("profile=Backend Developer");
        DeveloperProfileAssessment pa = new DeveloperProfileAssessment();
        pa.setProfileType(DeveloperProfileType.BACKEND_DEVELOPER);
        dto.setProfileAssessment(pa);
        dto.setLanguages(Map.of("Java", 3, "TypeScript", 1));
        dto.setPrimaryLanguage("Java");
        FeaturedRepositoryDTO fr = new FeaturedRepositoryDTO();
        fr.setName("repo1");
        fr.setDescription("Java Spring Boot");
        fr.setLanguage("Java");
        fr.setStars(5);
        fr.setForks(1);
        fr.setUpdatedAt("2024-01-01T00:00:00Z");
        fr.setFork(false);
        fr.setArchived(false);
        fr.setRecency("RECENT");
        fr.setTechnologies(List.of("Spring Boot", "Java"));
        dto.setFeaturedRepositories(List.of(fr));
        return dto;
    }

    @Test
    void reportEndpointReturnsSuccess() throws Exception {
        when(gitHubService.getOrGenerateReport(anyString())).thenReturn(sampleReport("alice"));
        mockMvc.perform(get("/api/github/report/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.languages.Java").value(3))
                .andExpect(jsonPath("$.primaryLanguage").value("Java"))
                .andExpect(jsonPath("$.featuredRepositories[0].name").value("repo1"));
    }

    @Test
    void finalReportSameCanonicalBehavior() throws Exception {
        FinalReportDTO report = sampleReport("bob");
        when(gitHubService.getOrGenerateReport(eq("bob"))).thenReturn(report);
        // both endpoints delegate to same service method
        mockMvc.perform(get("/api/github/report/bob")).andExpect(status().isOk());
        mockMvc.perform(get("/api/github/final-report/bob")).andExpect(status().isOk());
        verify(gitHubService, times(2)).getOrGenerateReport("bob");
    }

    @Test
    void notFoundMapsTo404() throws Exception {
        when(gitHubService.getOrGenerateReport(anyString())).thenThrow(new GitHubUserNotFoundException("ghost"));
        mockMvc.perform(get("/api/github/report/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rateLimitMapsTo429() throws Exception {
        when(gitHubService.getOrGenerateReport(anyString())).thenThrow(new GitHubRateLimitException("limit"));
        mockMvc.perform(get("/api/github/report/rateUser"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void authMapsTo503AuthFailed() throws Exception {
        when(gitHubService.getOrGenerateReport(anyString())).thenThrow(new GitHubAuthException("auth"));
        mockMvc.perform(get("/api/github/report/authUser"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GITHUB_AUTH_FAILED"));
    }

    @Test
    void unavailableMapsTo503() throws Exception {
        when(gitHubService.getOrGenerateReport(anyString())).thenThrow(new GitHubUnavailableException("timeout"));
        mockMvc.perform(get("/api/github/report/timeoutUser"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GITHUB_UNAVAILABLE"));
    }

    @Test
    void responseContainsRequiredFieldsAndNoLeak() throws Exception {
        when(gitHubService.getOrGenerateReport(anyString())).thenReturn(sampleReport("charlie"));
        MvcResult result = mockMvc.perform(get("/api/github/report/charlie"))
                .andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        String lower = json.toLowerCase();
        assertFalse(lower.contains("readmecontent"));
        assertFalse(lower.contains("dependencyevidence"));
        assertFalse(lower.contains("github.token"));
        assertFalse(lower.contains("gemini"));
        assertFalse(lower.contains("reportcache"));
        assertFalse(lower.contains("inflight"));
        assertFalse(lower.contains("api.key"));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        assertTrue(node.has("languages"));
        assertTrue(node.has("primaryLanguage"));
        assertTrue(node.has("featuredRepositories"));
        assertTrue(node.has("technologies"));
        assertTrue(node.has("overallScore"));
        assertTrue(node.has("aiAnalysis"));
        assertTrue(node.has("profileType"));
        assertTrue(node.has("confidence"));

        JsonNode fr = node.get("featuredRepositories").get(0);
        assertTrue(fr.has("name"));
        assertTrue(fr.has("technologies"));
        assertFalse(fr.has("readmeContent"));
    }

    @Test
    void legacyEndpointsStillExistButNotUsedByUnifiedFlow() throws Exception {
        // verify that /profile etc still work (mocked)
        when(gitHubService.getProfile(anyString())).thenReturn(new GitHubProfileDTO());
        mockMvc.perform(get("/api/github/profile/testuser")).andExpect(status().isOk());
        // ensure report endpoint is distinct from generic /{username}
        when(gitHubService.getRepositories(anyString())).thenReturn(List.of());
        mockMvc.perform(get("/api/github/someuser")).andExpect(status().isOk());
        // report should still be report, not generic
        when(gitHubService.getOrGenerateReport(anyString())).thenReturn(sampleReport("someuser"));
        mockMvc.perform(get("/api/github/report/someuser")).andExpect(jsonPath("$.username").value("someuser"));
    }
}
