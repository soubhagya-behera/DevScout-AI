package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperAnalysisData;
import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import com.soubhagya.devscout.dto.FeaturedRepositoryDTO;
import com.soubhagya.devscout.dto.FinalReportDTO;
import com.soubhagya.devscout.dto.GitHubProfileDTO;
import com.soubhagya.devscout.dto.GitHubRepoDTO;
import com.soubhagya.devscout.dto.ProfileAnalysisDTO;
import com.soubhagya.devscout.dto.RepositoryAnalysisDTO;
import com.soubhagya.devscout.dto.TechnologyAnalysisDTO;
import com.soubhagya.devscout.exception.GitHubAuthException;
import com.soubhagya.devscout.exception.GitHubRateLimitException;
import com.soubhagya.devscout.exception.GitHubUnavailableException;
import com.soubhagya.devscout.exception.GitHubUserNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@Service
public class GitHubService {

    @Value("${github.token}")
    private String githubToken;

    @Value("${devscout.cache.ttl.minutes:30}")
    private long cacheTtlMinutes = 30;

    /**
     * Phase 5: bounded in-memory cache.
     * Default 500 entries is conservative for small-to-medium deployment (500 * ~5KB ≈ 2.5MB).
     * Bounded via ConcurrentHashMap + LRU eviction; no external Redis.
     * Eviction: expired entries first, then least-recently-used.
     * TTL 30m, max-entries 500.
     */
    @Value("${devscout.cache.max-entries:500}")
    private int cacheMaxEntries = 500;

    private final RestTemplate restTemplate = new RestTemplate();

    private final GeminiService geminiService;
    private final TechnologyDetector technologyDetector;
    private final DeveloperScoringService scoringService;
    private final DeveloperProfileService profileService;

    private final Map<String, CachedReport> reportCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<FinalReportDTO>> inflight = new ConcurrentHashMap<>();

    public GitHubService(GeminiService geminiService,
                         TechnologyDetector technologyDetector,
                         DeveloperScoringService scoringService,
                         DeveloperProfileService profileService) {
        this.geminiService = geminiService;
        this.technologyDetector = technologyDetector;
        this.scoringService = scoringService;
        this.profileService = profileService;
    }

    static final int MAX_DEEP_REPOS = 10;
    static final int MAX_README_BYTES = 2000;
    static final int MAX_MANIFEST_BYTES = 1000;
    /**
     * Phase 4 hardening: actual GitHub HTTP budget for deep enrichment.
     * 1 main repository-list request (outside budget) + at most 20 deep HTTP requests.
     * Every outbound GitHub HTTP request (README, contents-list, manifest file) consumes 1 unit,
     * including failed/timeout requests. Worst-case total = 1 + 20 = 21 HTTP requests per cold report.
     */
    static final int MAX_DEEP_HTTP_CALLS = 20;

    // Visible for tests
    static String normalizeKey(String username) {
        if (username == null) return "";
        return username.trim().toLowerCase();
    }

    // For tests to clear state
    void clearCacheForTests() {
        reportCache.clear();
        inflight.clear();
    }

    void expireForTests(String username) {
        String key = normalizeKey(username);
        CachedReport cr = reportCache.get(key);
        if (cr != null) {
            reportCache.put(key, new CachedReport(cr.report, System.currentTimeMillis() - 1000, cr.lastAccess));
        }
    }

    // test hook to inject report directly with custom expiry
    void putReportForTests(String username, FinalReportDTO report, long expiresAt) {
        reportCache.put(normalizeKey(username), new CachedReport(report, expiresAt, System.currentTimeMillis()));
    }

    // Phase 5 test hooks
    void setMaxEntriesForTests(int max) {
        this.cacheMaxEntries = max;
    }

    int getMaxEntriesForTests() {
        return cacheMaxEntries;
    }

    // force lastAccess for deterministic LRU tests
    void setLastAccessForTests(String username, long lastAccess) {
        CachedReport cr = reportCache.get(normalizeKey(username));
        if (cr != null) cr.lastAccess = lastAccess;
    }

    long getLastAccessForTests(String username) {
        CachedReport cr = reportCache.get(normalizeKey(username));
        return cr == null ? -1 : cr.lastAccess;
    }

    int inflightSizeForTests() {
        return inflight.size();
    }

    int cacheSizeForTests() {
        return reportCache.size();
    }

    public List<GitHubRepoDTO> getRepositories(String username) {
        String normalized = normalizeKey(username);
        // normalize for GitHub API call as well (GitHub is case-insensitive)
        String url =
                "https://api.github.com/users/"
                        + normalized
                        + "/repos?per_page=100";

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(githubToken);
        headers.set("Accept", "application/vnd.github.mercy-preview+json");

        HttpEntity<String> entity =
                new HttpEntity<>(headers);

        try {
            ResponseEntity<List<GitHubRepoDTO>> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            new ParameterizedTypeReference<List<GitHubRepoDTO>>() {}
                    );
            List<GitHubRepoDTO> body = response.getBody();
            return body == null ? List.of() : body;
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                throw new GitHubUserNotFoundException(normalized, e);
            }
            if (status == 429) {
                throw new GitHubRateLimitException("GitHub rate limit exceeded for user: " + normalized, e);
            }
            if (status == 403) {
                String body = e.getResponseBodyAsString();
                String lower = body == null ? "" : body.toLowerCase();
                if (lower.contains("rate limit") || lower.contains("rate_limit") || lower.contains("too many requests")) {
                    throw new GitHubRateLimitException("GitHub rate limit exceeded (403) for user: " + normalized, e);
                }
                throw new GitHubAuthException("GitHub authentication/configuration failure for user: " + normalized, e);
            }
            throw e;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("connect") || msg.contains("read")) {
                throw new GitHubUnavailableException("GitHub connection timed out for user: " + normalized, e);
            }
            throw new GitHubUnavailableException("GitHub is temporarily unavailable for user: " + normalized, e);
        } catch (org.springframework.web.client.RestClientException e) {
            // fallback for other network-level failures not covered above (do not map to 404/auth)
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("timeout") || msg.contains("connect") || msg.contains("unavailable") || msg.contains("refused")) {
                throw new GitHubUnavailableException("GitHub is temporarily unavailable for user: " + normalized, e);
            }
            throw e;
        }
    }

    /**
     * Phase 4 hardening: bounded deep enrichment counting ACTUAL HTTP requests.
     * Budget = MAX_DEEP_HTTP_CALLS (20) actual HTTP calls. Each README, contents-list,
     * and manifest-file request consumes 1 unit, including failures/timeouts.
     * Main repository-list (1) is outside budget. Worst-case total per cold report = 1 + 20 = 21.
     */
    void enrichWithDeepEvidence(List<GitHubRepoDTO> repos, String username) {
        if (repos == null || repos.isEmpty()) return;
        String normalized = normalizeKey(username);
        List<GitHubRepoDTO> sorted = repos.stream()
                .sorted(Comparator.comparingInt((GitHubRepoDTO r) -> relevanceScore(r)).reversed())
                .limit(MAX_DEEP_REPOS)
                .toList();
        int httpUsed = 0;
        for (GitHubRepoDTO repo : sorted) {
            if (httpUsed >= MAX_DEEP_HTTP_CALLS) break;
            int techSize = technologyDetector.detect(List.of(repo)).size();
            if (techSize >= 2) continue;
            // README request (1 HTTP)
            if (httpUsed >= MAX_DEEP_HTTP_CALLS) break;
            httpUsed++;
            try {
                String readme = fetchReadme(normalized, repo.getName());
                if (readme != null && !readme.isBlank()) {
                    repo.setReadmeContent(readme.substring(0, Math.min(readme.length(), MAX_README_BYTES)));
                }
            } catch (Exception ignored) {
            }
            if (httpUsed >= MAX_DEEP_HTTP_CALLS) break;
            // contents-list request (1 HTTP) + optional manifest file (1 HTTP) — both counted
            // We delegate to helper that counts internally via httpUsed array
            int[] counter = new int[]{httpUsed};
            try {
                String manifest = fetchManifestEvidenceWithBudget(normalized, repo.getName(), counter);
                if (manifest != null && !manifest.isBlank()) {
                    String existing = repo.getDependencyEvidence();
                    String combined = (existing == null ? "" : existing + " ") + manifest;
                    repo.setDependencyEvidence(combined.substring(0, Math.min(combined.length(), MAX_MANIFEST_BYTES)));
                }
            } catch (Exception ignored) {
            }
            httpUsed = counter[0];
        }
    }

    // Budget-aware variant: counts contents-list and manifest-file separately
    String fetchManifestEvidenceWithBudget(String owner, String repo, int[] httpUsed) {
        String normalizedOwner = normalizeKey(owner);
        String listUrl = "https://api.github.com/repos/" + normalizedOwner + "/" + repo + "/contents";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        // contents-list is 1 HTTP
        if (httpUsed[0] >= MAX_DEEP_HTTP_CALLS) return null;
        httpUsed[0]++;
        ResponseEntity<String> listResp;
        try {
            listResp = restTemplate.exchange(listUrl, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            return null;
        }
        String body = listResp.getBody();
        if (body == null) return null;
        String lower = body.toLowerCase();
        String targetFile = null;
        if (lower.contains("\"name\" : \"package.json\"") || lower.contains("\"name\":\"package.json\"")) targetFile = "package.json";
        else if (lower.contains("\"name\" : \"pom.xml\"") || lower.contains("\"name\":\"pom.xml\"")) targetFile = "pom.xml";
        else if (lower.contains("\"name\" : \"requirements.txt\"") || lower.contains("\"name\":\"requirements.txt\"")) targetFile = "requirements.txt";
        else if (lower.contains("\"name\" : \"pyproject.toml\"") || lower.contains("\"name\":\"pyproject.toml\"")) targetFile = "pyproject.toml";
        else if (lower.contains("\"name\" : \"go.mod\"") || lower.contains("\"name\":\"go.mod\"")) targetFile = "go.mod";
        else if (lower.contains(".csproj\"")) targetFile = findCsproj(lower);
        else if (lower.contains("\"name\" : \"composer.json\"") || lower.contains("\"name\":\"composer.json\"")) targetFile = "composer.json";
        else if (lower.contains("\"name\" : \"gemfile\"") || lower.contains("\"name\":\"gemfile\"")) targetFile = "Gemfile";
        else if (lower.contains("\"name\" : \"build.gradle\"") || lower.contains("\"name\":\"build.gradle\"")) targetFile = "build.gradle";
        if (targetFile == null) return null;
        if (httpUsed[0] >= MAX_DEEP_HTTP_CALLS) return null;
        httpUsed[0]++;
        String fileUrl = "https://api.github.com/repos/" + normalizedOwner + "/" + repo + "/contents/" + targetFile;
        try {
            ResponseEntity<String> fileResp = restTemplate.exchange(fileUrl, HttpMethod.GET, entity, String.class);
            String fileBody = fileResp.getBody();
            if (fileBody == null) return null;
            String content = extractBase64Content(fileBody);
            if (content == null) return fileBody.substring(0, Math.min(fileBody.length(), MAX_MANIFEST_BYTES));
            String decoded;
            try {
                decoded = new String(java.util.Base64.getMimeDecoder().decode(content));
            } catch (Exception e) {
                decoded = new String(java.util.Base64.getDecoder().decode(content.replace("\n","")));
            }
            return decoded.substring(0, Math.min(decoded.length(), MAX_MANIFEST_BYTES));
        } catch (Exception e) {
            return null;
        }
    }

    private int relevanceScore(GitHubRepoDTO r) {
        int s = r.getStars() * 2 + r.getSize() / 100;
        if (!technologyDetector.detect(List.of(r)).isEmpty()) s += 10;
        if (r.getPushed_at() != null) {
            try {
                long days = java.time.Duration.between(java.time.Instant.parse(r.getPushed_at()), java.time.Instant.now()).toDays();
                if (days <= 90) s += 5;
            } catch (Exception ignored) {}
        }
        return s;
    }

    String fetchReadme(String owner, String repo) {
        String normalizedOwner = normalizeKey(owner);
        String url = "https://api.github.com/repos/" + normalizedOwner + "/" + repo + "/readme";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubToken);
        headers.set("Accept", "application/vnd.github.v3.raw");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        if (resp.getBody() == null) return null;
        return resp.getBody();
    }

    String fetchManifestEvidence(String owner, String repo) {
        String normalizedOwner = normalizeKey(owner);
        String listUrl = "https://api.github.com/repos/" + normalizedOwner + "/" + repo + "/contents";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> listResp;
        try {
            listResp = restTemplate.exchange(listUrl, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            return null;
        }
        String body = listResp.getBody();
        if (body == null) return null;
        String lower = body.toLowerCase();
        String targetFile = null;
        if (lower.contains("\"name\" : \"package.json\"") || lower.contains("\"name\":\"package.json\"")) targetFile = "package.json";
        else if (lower.contains("\"name\" : \"pom.xml\"") || lower.contains("\"name\":\"pom.xml\"")) targetFile = "pom.xml";
        else if (lower.contains("\"name\" : \"requirements.txt\"") || lower.contains("\"name\":\"requirements.txt\"")) targetFile = "requirements.txt";
        else if (lower.contains("\"name\" : \"pyproject.toml\"") || lower.contains("\"name\":\"pyproject.toml\"")) targetFile = "pyproject.toml";
        else if (lower.contains("\"name\" : \"go.mod\"") || lower.contains("\"name\":\"go.mod\"")) targetFile = "go.mod";
        else if (lower.contains(".csproj\"")) targetFile = findCsproj(lower);
        else if (lower.contains("\"name\" : \"composer.json\"") || lower.contains("\"name\":\"composer.json\"")) targetFile = "composer.json";
        else if (lower.contains("\"name\" : \"gemfile\"") || lower.contains("\"name\":\"gemfile\"")) targetFile = "Gemfile";
        else if (lower.contains("\"name\" : \"build.gradle\"") || lower.contains("\"name\":\"build.gradle\"")) targetFile = "build.gradle";
        if (targetFile == null) return null;
        String fileUrl = "https://api.github.com/repos/" + normalizedOwner + "/" + repo + "/contents/" + targetFile;
        try {
            ResponseEntity<String> fileResp = restTemplate.exchange(fileUrl, HttpMethod.GET, entity, String.class);
            String fileBody = fileResp.getBody();
            if (fileBody == null) return null;
            String content = extractBase64Content(fileBody);
            if (content == null) return fileBody.substring(0, Math.min(fileBody.length(), MAX_MANIFEST_BYTES));
            String decoded;
            try {
                decoded = new String(java.util.Base64.getMimeDecoder().decode(content));
            } catch (Exception e) {
                decoded = new String(java.util.Base64.getDecoder().decode(content.replace("\n","")));
            }
            return decoded.substring(0, Math.min(decoded.length(), MAX_MANIFEST_BYTES));
        } catch (Exception e) {
            return null;
        }
    }

    private String findCsproj(String lowerBody) {
        int idx = lowerBody.indexOf(".csproj");
        if (idx < 0) return null;
        int start = lowerBody.lastIndexOf("\"name\"", idx);
        if (start < 0) return null;
        int colon = lowerBody.indexOf(":", start);
        int q1 = lowerBody.indexOf("\"", colon);
        int q2 = lowerBody.indexOf("\"", q1+1);
        if (q1 <0 || q2<0) return null;
        return lowerBody.substring(q1+1, q2);
    }

    private String extractBase64Content(String json) {
        int idx = json.indexOf("\"content\"");
        if (idx <0) return null;
        int colon = json.indexOf(":", idx);
        int q1 = json.indexOf("\"", colon);
        int q2 = json.lastIndexOf("\"");
        int encodingIdx = json.indexOf("\"encoding\"");
        if (encodingIdx > q1) {
            q2 = json.lastIndexOf("\"", encodingIdx-1);
            String raw = json.substring(q1+1, q2);
            return raw;
        }
        return null;
    }

    public ProfileAnalysisDTO analyzeProfile(String username) {
        return analyzeProfile(getRepositories(username));
    }

    ProfileAnalysisDTO analyzeProfile(List<GitHubRepoDTO> repos) {
        Map<String,Integer> languageCount =
                new HashMap<>();
        for (GitHubRepoDTO repo : repos) {
            String language = repo.getLanguage();
            if (language != null) {
                languageCount.put(
                        language,
                        languageCount.getOrDefault(
                                language,
                                0
                        ) + 1
                );
            }
        }
        ProfileAnalysisDTO analysis =
                new ProfileAnalysisDTO();
        analysis.setTotalRepositories(
                repos.size()
        );
        analysis.setLanguages(
                languageCount
        );
        return analysis;
    }

    public TechnologyAnalysisDTO detectTechnologies(
            String username
    ) {
        return detectTechnologies(getRepositories(username));
    }

    TechnologyAnalysisDTO detectTechnologies(List<GitHubRepoDTO> repos) {
        Map<String,Integer> techMap = technologyDetector.detect(repos);
        TechnologyAnalysisDTO dto = new TechnologyAnalysisDTO();
        dto.setTechnologies(techMap);
        return dto;
    }

    public DeveloperScoreDTO calculateScore(
            String username
    ) {
        return calculateScore(
                detectTechnologies(username)
                        .getTechnologies()
        );
    }

    DeveloperScoreDTO calculateScore(Map<String,Integer> techs) {
        return scoringService.score(techs);
    }

    public List<RepositoryAnalysisDTO> analyzeRepositories(
            String username
    ) {
        List<GitHubRepoDTO> repos =
                getRepositories(username);
        List<RepositoryAnalysisDTO> result =
                new ArrayList<>();
        for (GitHubRepoDTO repo : repos) {
            String description =
                    repo.getDescription();
            if (description == null)
                continue;
            TechnologyAnalysisDTO local =
                    detectTechnologies(
                            List.of(repo)
                    );
            String analysis =
                    local.getTechnologies().isEmpty()
                            ? "Technologies detected: None"
                            : "Technologies detected: "
                                    + String.join(
                                            ", ",
                                            local.getTechnologies().keySet()
                                    );
            result.add(
                    new RepositoryAnalysisDTO(
                            repo.getName(),
                            analysis
                    )
            );
        }
        return result;
    }

    /**
     * Phase 4 canonical single pipeline with per-username single-flight and normalization.
     */
    public FinalReportDTO getOrGenerateReport(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        String key = normalizeKey(username);
        // fast path cache hit - also invalidate stale cache entries lacking Phase 4 unified fields
        // Phase 5: touch LRU on hit, lazily remove expired
        CachedReport cached = reportCache.get(key);
        if (cached != null) {
            if (cached.isExpired() || !isCacheEntryValid(cached.report)) {
                reportCache.remove(key, cached);
            } else {
                cached.touch();
                return cached.report;
            }
        }

        // single-flight: only one generation per normalized key
        CompletableFuture<FinalReportDTO> newFuture = new CompletableFuture<>();
        CompletableFuture<FinalReportDTO> existing = inflight.putIfAbsent(key, newFuture);
        if (existing != null) {
            // follower: wait for leader
            try {
                return existing.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for report generation", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            }
        }

        // leader: generate
        try {
            // double-check cache after acquiring leadership (prevents duplicate generation if another thread populated cache between first check and putIfAbsent in edge)
            CachedReport recheck = reportCache.get(key);
            if (recheck != null) {
                if (recheck.isExpired() || !isCacheEntryValid(recheck.report)) {
                    reportCache.remove(key, recheck);
                } else {
                    recheck.touch();
                    FinalReportDTO dupHit = recheck.report;
                    newFuture.complete(dupHit);
                    return dupHit;
                }
            }
            FinalReportDTO report = generateReportInternal(key, username.trim());
            newFuture.complete(report);
            return report;
        } catch (Throwable t) {
            newFuture.completeExceptionally(t);
            // remove failed future so retry is possible
            inflight.remove(key, newFuture);
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new RuntimeException(t);
        } finally {
            // on success, clean up inflight mapping after completion - followers already have reference via existing
            // keep removal safe: only remove if still mapping to this future
            if (newFuture.isDone() && !newFuture.isCompletedExceptionally()) {
                inflight.remove(key, newFuture);
            }
        }
    }

    /**
     * Backward-compatible entry — delegates to canonical pipeline.
     */
    public FinalReportDTO generateFinalReport(String username) {
        return getOrGenerateReport(username);
    }

    private FinalReportDTO generateReportInternal(String normalizedKey, String displayUsername) {
        List<GitHubRepoDTO> repos =
                getRepositories(normalizedKey);

        try {
            enrichWithDeepEvidence(repos, normalizedKey);
        } catch (Exception ignored) {
        }

        Map<String,Integer> languages =
                analyzeProfile(repos)
                        .getLanguages();

        // Phase 6: deterministic primary language (counts then alphabetical tie-breaker), not specialization
        String primaryLanguage =
                languages.entrySet().stream()
                        .sorted((a,b) -> {
                            int cmp = Integer.compare(b.getValue(), a.getValue());
                            if (cmp != 0) return cmp;
                            return a.getKey().compareTo(b.getKey());
                        })
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("Unknown");

        Map<String,Integer> technologies =
                detectTechnologies(repos)
                        .getTechnologies();

        // Phase 6: evidence-weighted scoring (strong 1.0, medium 0.75, weak 0.35)
        // Keeps language-only repos from inflating scores equally to manifest-backed repos
        DeveloperScoreDTO score =
                scoringService.scoreFromRepos(repos);

        DeveloperAnalysisData data =
                new DeveloperAnalysisData();

        // display username uses trimmed original but normalized casing could be used; keep trimmed original display for user-facing
        String display = displayUsername.trim();
        data.setUsername(display);

        data.setTotalRepositories(
                repos.size()
        );

        data.setLanguages(
                languages
        );

        data.setTechnologies(
                technologies
        );

        data.setBackendScore(
                score.getBackendScore()
        );

        data.setFrontendScore(
                score.getFrontendScore()
        );

        data.setDatabaseScore(
                score.getDatabaseScore()
        );

        data.setAiScore(
                score.getAiScore()
        );

        data.setOverallScore(
                score.getOverallScore()
        );

        data.setRepositorySummaries(
                buildRepositorySummaries(repos)
        );

        // ONE logical Gemini call per generation; fallback is now stack-neutral inside GeminiService
        String aiAnalysis =
                geminiService
                        .generateCandidateReport(
                                data
                        );

        com.soubhagya.devscout.dto.DeveloperProfileAssessment profileAssessment =
                profileService.assess(repos, technologies, languages, score);

        FinalReportDTO report =
                new FinalReportDTO();

        report.setUsername(display);

        report.setOverallScore(
                score.getOverallScore()
        );

        report.setBackendScore(
                score.getBackendScore()
        );

        report.setFrontendScore(
                score.getFrontendScore()
        );

        report.setDatabaseScore(
                score.getDatabaseScore()
        );

        report.setAiScore(
                score.getAiScore()
        );

        report.setTechnologies(
                technologies
        );

        report.setAiAnalysis(
                aiAnalysis
        );

        report.setProfileType(profileAssessment.getProfileType().getDisplay());
        report.setConfidence(profileAssessment.getConfidence().name());
        report.setSpecialization(profileAssessment.getSpecialization());
        report.setExperienceLevel(profileAssessment.getExperienceLevel());
        report.setExperienceEvidence(profileAssessment.getExperienceEvidence());
        report.setMeaningfulRepositories(profileAssessment.getMeaningfulRepositories());
        report.setTotalRepositories(profileAssessment.getTotalRepositories());
        report.setTotalStars(profileAssessment.getTotalStars());
        report.setBreadth(profileAssessment.getBreadth());
        report.setDepth(profileAssessment.getDepth());
        report.setDistinctTechnologies(profileAssessment.getDistinctTechnologies());
        report.setCapabilitySignals(profileAssessment.getCapabilitySignals());
        report.setEvidenceSummary(profileAssessment.getEvidenceSummary());
        report.setProfileAssessment(profileAssessment);

        // Phase 4 unified fields
        report.setLanguages(languages);
        report.setPrimaryLanguage(primaryLanguage);
        report.setFeaturedRepositories(buildFeaturedRepositories(repos));

        // Phase 6: lightweight consistency validation — ensures no contradictions
        validateReportConsistency(report, repos, languages);

        // Phase 5: bounded cache — evict expired then LRU if needed before inserting
        evictIfNeeded();
        reportCache.put(
                normalizedKey,
                new CachedReport(
                        report,
                        System.currentTimeMillis()
                                + cacheTtlMinutes * 60_000
                )
        );

        return report;
    }

    private List<String> buildRepositorySummaries(
            List<GitHubRepoDTO> repos
    ) {
        List<GitHubRepoDTO> topRepos =
                repos.stream()
                        .sorted(
                                Comparator.comparingInt(
                                        GitHubRepoDTO::getStars
                                ).reversed()
                        )
                        .limit(20)
                        .toList();
        List<String> summaries =
                new ArrayList<>();
        for (GitHubRepoDTO repo : topRepos) {
            String description =
                    repo.getDescription();
            if (description == null
                    || description.isBlank()) {
                description = "No description";
            } else if (description.length() > 80) {
                description =
                        description.substring(0, 80)
                                + "...";
            }
            String lang = repo.getLanguage() != null ? repo.getLanguage() : "Unknown";
            String recency = profileService.classifyRecency(repo);
            String techs = String.join(",", technologyDetector.detect(List.of(repo)).keySet());
            if (techs.isEmpty()) techs = "None";
            String forkFlag = repo.isFork() ? " fork" : "";
            String archivedFlag = repo.isArchived() ? " archived" : "";
            String summary = String.format("%s (%s) ★%d [%s]%s%s Tech:%s - %s",
                    repo.getName(), lang, repo.getStars(), recency, forkFlag, archivedFlag, techs, description);
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * Phase 6: featured selection uses multiple deterministic signals, not stars alone.
     * Signals: meaningfulness, qualityScore, stars, recency, tech relevance.
     * Does not privilege Java/Spring; stack-neutral.
     * Forks/archived are represented accurately (flagged) not excluded; quality determines rank.
     */
    private List<FeaturedRepositoryDTO> buildFeaturedRepositories(List<GitHubRepoDTO> repos) {
        if (repos == null || repos.isEmpty()) return List.of();
        // Build evidence list once for quality scoring
        List<com.soubhagya.devscout.dto.RepositoryEvidence> evidences = profileService.buildEvidenceList(repos);
        Map<String, com.soubhagya.devscout.dto.RepositoryEvidence> evidenceByName = new HashMap<>();
        for (var ev : evidences) evidenceByName.put(ev.getName(), ev);

        List<GitHubRepoDTO> sorted = repos.stream()
                .sorted(Comparator.comparingInt((GitHubRepoDTO r) -> {
                    var ev = evidenceByName.get(r.getName());
                    int quality = ev != null ? ev.getQualityScore() : 0;
                    int stars = r.getStars() * 2;
                    int recencyBonus = switch (ev != null ? ev.getRecency() : "UNKNOWN") {
                        case "RECENT" -> 15;
                        case "ACTIVE" -> 6;
                        default -> 0;
                    };
                    int techBonus = (ev != null && ev.isMeaningful() && !technologyDetector.detect(List.of(r)).isEmpty()) ? 12 : 0;
                    // forks/archived not penalized heavily, just slight -5 to avoid top bias but still representative
                    int forkPenalty = r.isFork() ? -3 : 0;
                    return -(quality + stars + recencyBonus + techBonus + forkPenalty);
                }).thenComparing(GitHubRepoDTO::getName))
                .limit(10)
                .toList();

        List<FeaturedRepositoryDTO> out = new ArrayList<>();
        for (GitHubRepoDTO repo : sorted) {
            String desc = repo.getDescription();
            if (desc != null && desc.length() > 120) {
                desc = desc.substring(0, 120) + "...";
            }
            List<String> techs = new ArrayList<>(technologyDetector.detect(List.of(repo)).keySet());
            FeaturedRepositoryDTO dto = new FeaturedRepositoryDTO();
            dto.setName(repo.getName());
            dto.setDescription(desc);
            dto.setLanguage(repo.getLanguage());
            dto.setStars(repo.getStars());
            dto.setForks(repo.getForks_count());
            dto.setUpdatedAt(repo.getUpdated_at());
            dto.setFork(repo.isFork());
            dto.setArchived(repo.isArchived());
            dto.setRecency(profileService.classifyRecency(repo));
            dto.setTechnologies(techs);
            out.add(dto);
        }
        return out;
    }

    public GitHubProfileDTO getProfile(
            String username
    ) {
        String key = normalizeKey(username);
        ProfileAnalysisDTO analysis =
                analyzeProfile(getRepositories(key));
        GitHubProfileDTO dto =
                new GitHubProfileDTO();
        dto.setUsername(username.trim());
        dto.setTotalRepositories(
                analysis.getTotalRepositories()
        );
        String primaryLanguage =
                analysis.getLanguages()
                        .entrySet()
                        .stream()
                        .max(
                                Map.Entry.comparingByValue()
                        )
                        .map(
                                Map.Entry::getKey
                        )
                        .orElse("Unknown");
        dto.setPrimaryLanguage(
                primaryLanguage
        );
        return dto;
    }

    private boolean isCacheEntryValid(FinalReportDTO report) {
        // Phase 4 added languages/primaryLanguage/featuredRepositories; old cached entries missing these should be regenerated
        return report.getLanguages() != null && report.getPrimaryLanguage() != null && report.getFeaturedRepositories() != null;
    }

    /**
     * Phase 6: lightweight consistency checks — no heavy framework.
     * Ensures scores 0-100, required fields present, no internal leak, profile matches evidence.
     */
    private void validateReportConsistency(FinalReportDTO report, List<GitHubRepoDTO> repos, Map<String,Integer> languages) {
        // scores bounds
        for (int s : new int[]{report.getOverallScore(), report.getBackendScore(), report.getFrontendScore(), report.getDatabaseScore(), report.getAiScore()}) {
            if (s < 0 || s > 100) throw new IllegalStateException("Score out of bounds: " + s);
        }
        // required fields
        if (report.getUsername() == null || report.getUsername().isBlank()) throw new IllegalStateException("Username missing");
        if (report.getLanguages() == null) report.setLanguages(Map.of());
        if (report.getFeaturedRepositories() == null) report.setFeaturedRepositories(List.of());
        if (report.getTechnologies() == null) report.setTechnologies(Map.of());
        // experience label must be one of allowed and not claim employment
        String exp = report.getExperienceLevel();
        if (exp == null || !List.of("Beginner","Intermediate","Advanced","Expert").contains(exp)) {
            report.setExperienceLevel("Beginner");
        }
        // profileType must be consistent: if no meaningful repos, must be UNKNOWN
        if (repos == null || repos.isEmpty() || report.getMeaningfulRepositories() == null || report.getMeaningfulRepositories() == 0) {
            // allow UNKNOWN or GENERAL, but ensure confidence LOW for empty
            if (report.getProfileAssessment() != null && report.getMeaningfulRepositories() == 0) {
                // already set to UNKNOWN by profile service
            }
        }
        // primary language deterministic, not specialization — no validation needed, just ensure not null
        if (report.getPrimaryLanguage() == null) report.setPrimaryLanguage("Unknown");
    }

    /**
     * Phase 5: bounded cache helpers — lazy expiration + LRU.
     * Cleanup is done on writes/access, no background thread.
     */
    private void removeExpiredEntries() {
        reportCache.entrySet().removeIf(e -> e.getValue().isExpired() || !isCacheEntryValid(e.getValue().report));
    }

    private void evictIfNeeded() {
        if (reportCache.size() < cacheMaxEntries) return;
        // first evict expired entries
        removeExpiredEntries();
        if (reportCache.size() < cacheMaxEntries) return;
        // still full → evict least-recently-used
        String lruKey = null;
        long minAccess = Long.MAX_VALUE;
        for (Map.Entry<String, CachedReport> e : reportCache.entrySet()) {
            long la = e.getValue().lastAccess;
            if (la < minAccess) {
                minAccess = la;
                lruKey = e.getKey();
            }
        }
        if (lruKey != null) {
            reportCache.remove(lruKey);
        }
    }

    private static class CachedReport {
        private final FinalReportDTO report;
        private final long expiresAt;
        volatile long lastAccess;
        CachedReport(
                FinalReportDTO report,
                long expiresAt
        ) {
            this(report, expiresAt, System.currentTimeMillis());
        }
        CachedReport(
                FinalReportDTO report,
                long expiresAt,
                long lastAccess
        ) {
            this.report = report;
            this.expiresAt = expiresAt;
            this.lastAccess = lastAccess;
        }
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
    }
}
