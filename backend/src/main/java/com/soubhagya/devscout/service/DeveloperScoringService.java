package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.DeveloperScoreDTO;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Stack-agnostic deterministic scoring.
 *
 * Principles:
 * - Evidence, not prestige: every backend tech contributes equally to BACKEND, etc.
 * - Normalized 0-100: base 20 + 15*signalCount per capability, capped at 100.
 * - Per-repo deduplication already handled in TechnologyDetector, so signalCount
 *   approximates both breadth (distinct techs) and depth (repos).
 * - Specialization-aware overall: weighted average favoring strengths
 *   (0.4*max +0.3*second +0.2*third +0.1*fourth). This avoids penalizing a
 *   backend specialist for low AI, and avoids max-dominance.
 * - Unknown technologies gracefully ignored.
 */
@Component
public class DeveloperScoringService {

    private static final int BASE = 20;
    private static final int PER_SIGNAL = 15;
    private static final int CAP = 100;

    // overall weighting
    private static final double W1 = 0.4;
    private static final double W2 = 0.3;
    private static final double W3 = 0.2;
    private static final double W4 = 0.1;

    private final TechnologyDetector detector;

    public DeveloperScoringService(TechnologyDetector detector) {
        this.detector = detector;
    }

    /**
     * Original integer-count scoring (kept for backward tests).
     */
    public DeveloperScoreDTO score(Map<String, Integer> technologies) {
        if (technologies == null) technologies = Collections.emptyMap();

        int backendCount = countSignals(technologies, TechnologyDetector.Capability.BACKEND);
        int frontendCount = countSignals(technologies, TechnologyDetector.Capability.FRONTEND);
        int databaseCount = countSignals(technologies, TechnologyDetector.Capability.DATABASE);
        int aiCount = countSignals(technologies, TechnologyDetector.Capability.AI);

        return scoreFromCounts(backendCount, frontendCount, databaseCount, aiCount);
    }

    /**
     * Phase 6: evidence-weighted scoring (linear, kept for backward unit tests).
     * Weighted count uses per-repo strength: STRONG=1.0, MEDIUM=0.65, WEAK=0.30.
     * Formula: score = min(100, BASE + round(weightedCount * PER_SIGNAL)), BASE=20, PER_SIGNAL=15.
     */
    public DeveloperScoreDTO scoreWeighted(Map<String, Double> weightedCounts) {
        if (weightedCounts == null) weightedCounts = Collections.emptyMap();
        double backendW = sumWeighted(weightedCounts, TechnologyDetector.Capability.BACKEND);
        double frontendW = sumWeighted(weightedCounts, TechnologyDetector.Capability.FRONTEND);
        double databaseW = sumWeighted(weightedCounts, TechnologyDetector.Capability.DATABASE);
        double aiW = sumWeighted(weightedCounts, TechnologyDetector.Capability.AI);

        int backend = toScoreWeighted(backendW);
        int frontend = toScoreWeighted(frontendW);
        int database = toScoreWeighted(databaseW);
        int ai = toScoreWeighted(aiW);

        int overall = computeOverall(backend, frontend, database, ai);
        DeveloperScoreDTO dto = new DeveloperScoreDTO();
        dto.setBackendScore(backend);
        dto.setFrontendScore(frontend);
        dto.setDatabaseScore(database);
        dto.setAiScore(ai);
        dto.setOverallScore(overall);
        return dto;
    }

    /**
     * Phase 10: calibrated scoring with evidence quality and diminishing returns.
     * - Evidence strength: STRONG 1.0, MEDIUM 0.65, WEAK 0.30
     * - Repository quality factor (fork/archived/size/stars/recency) 0.35-1.0
     * - Diminishing returns per capability (harmonic): effective = sum w_i / (1 + 0.38*i)
     *   First signal full, second ~0.73, third ~0.57, etc. Broad shallow repos saturate slower.
     * Keeps stack-neutral, deterministic, 0-100.
     */
    public DeveloperScoreDTO scoreFromRepos(List<com.soubhagya.devscout.dto.GitHubRepoDTO> repos) {
        if (repos == null || repos.isEmpty()) return score(Map.of());
        // Phase 10.1: per-repository per-capability cap — one repo contributes at most once per capability
        Map<TechnologyDetector.Capability, List<Double>> perCap = new EnumMap<>(TechnologyDetector.Capability.class);
        for (TechnologyDetector.Capability c : TechnologyDetector.Capability.values()) perCap.put(c, new java.util.ArrayList<>());

        for (com.soubhagya.devscout.dto.GitHubRepoDTO repo : repos) {
            Map<String, TechnologyDetector.EvidenceStrength> perRepo = detector.detectStrengthPerRepo(repo);
            double q = qualityFactor(repo);
            // Collapse to max per capability for this repo
            Map<TechnologyDetector.Capability, Double> repoCapMax = new EnumMap<>(TechnologyDetector.Capability.class);
            for (Map.Entry<String, TechnologyDetector.EvidenceStrength> e : perRepo.entrySet()) {
                TechnologyDetector.Capability cap = detector.capabilityForDisplay(e.getKey());
                if (cap == null) continue;
                if (cap == TechnologyDetector.Capability.DEVOPS) continue;
                double w = detector.weightForStrength(e.getValue()) * q;
                repoCapMax.merge(cap, w, Math::max);
            }
            for (Map.Entry<TechnologyDetector.Capability, Double> entry : repoCapMax.entrySet()) {
                perCap.get(entry.getKey()).add(entry.getValue());
            }
        }
        double backendEff = diminishingSum(perCap.get(TechnologyDetector.Capability.BACKEND));
        double frontendEff = diminishingSum(perCap.get(TechnologyDetector.Capability.FRONTEND));
        double databaseEff = diminishingSum(perCap.get(TechnologyDetector.Capability.DATABASE));
        double aiEff = diminishingSum(perCap.get(TechnologyDetector.Capability.AI));

        // If no effective signals at all, fall back to empty
        if (backendEff == 0 && frontendEff == 0 && databaseEff == 0 && aiEff == 0) return score(Map.of());

        int backend = toScoreWeighted(backendEff);
        int frontend = toScoreWeighted(frontendEff);
        int database = toScoreWeighted(databaseEff);
        int ai = toScoreWeighted(aiEff);

        int overall = computeOverall(backend, frontend, database, ai);
        DeveloperScoreDTO dto = new DeveloperScoreDTO();
        dto.setBackendScore(backend);
        dto.setFrontendScore(frontend);
        dto.setDatabaseScore(database);
        dto.setAiScore(ai);
        dto.setOverallScore(overall);
        return dto;
    }

    // Diminishing returns: harmonic decay 1/(1+0.38*i), sorted descending ensures strong first
    double diminishingSum(List<Double> weights) {
        if (weights == null || weights.isEmpty()) return 0;
        weights.sort(java.util.Collections.reverseOrder());
        double sum = 0;
        for (int i = 0; i < weights.size(); i++) {
            double decay = 1.0 / (1.0 + 0.38 * i);
            sum += weights.get(i) * decay;
        }
        return sum;
    }

    // Repository quality 0.35-1.0, stack-neutral, explainable
    double qualityFactor(com.soubhagya.devscout.dto.GitHubRepoDTO repo) {
        double q = 1.0;
        if (repo.isFork()) q *= 0.65;
        if (repo.isArchived()) q *= 0.80;
        int size = repo.getSize();
        if (size < 20) q *= 0.60;
        else if (size < 80) q *= 0.82;
        // stars: small bonus, not dominant
        int stars = repo.getStars();
        if (stars >= 10) q *= 1.08;
        else if (stars >= 3) q *= 1.03;
        // recency
        String rec = classifyRecency(repo);
        if ("RECENT".equals(rec)) q *= 1.0;
        else if ("ACTIVE".equals(rec)) q *= 0.92;
        else if ("STALE".equals(rec)) q *= 0.70;
        else q *= 0.85; // UNKNOWN
        if (q < 0.35) q = 0.35;
        if (q > 1.0) q = 1.0;
        return q;
    }

    String classifyRecency(com.soubhagya.devscout.dto.GitHubRepoDTO repo) {
        String ts = repo.getPushed_at() != null ? repo.getPushed_at() : repo.getUpdated_at();
        if (ts == null || ts.isBlank()) return "UNKNOWN";
        try {
            java.time.Instant instant = java.time.Instant.parse(ts);
            long days = java.time.Duration.between(instant, java.time.Instant.now()).toDays();
            if (days <= 90) return "RECENT";
            if (days <= 365) return "ACTIVE";
            return "STALE";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private DeveloperScoreDTO scoreFromCounts(int backendCount, int frontendCount, int databaseCount, int aiCount) {
        int backend = toScore(backendCount);
        int frontend = toScore(frontendCount);
        int database = toScore(databaseCount);
        int ai = toScore(aiCount);
        int overall = computeOverall(backend, frontend, database, ai);
        DeveloperScoreDTO dto = new DeveloperScoreDTO();
        dto.setBackendScore(backend);
        dto.setFrontendScore(frontend);
        dto.setDatabaseScore(database);
        dto.setAiScore(ai);
        dto.setOverallScore(overall);
        return dto;
    }

    /**
     * Experience level derived deterministically from overall + repo/exposure signals.
     * Keeps frontend contract Beginner/Intermediate/Advanced/Expert.
     * Conservative: few repos caps level even if overall high (prevents 1-repo Expert).
     */
    public String experienceLevel(int overall, int totalRepos, int totalStars) {
        // Guard: empty profile → Beginner
        if (totalRepos <= 0) return "Beginner";

        // Conservative repo guard: downgrade if not enough repos
        if (overall >= 80) {
            if (totalRepos >= 5 && totalStars >= 5) return "Expert";
            if (totalRepos >= 3) return "Advanced";
            if (totalRepos == 2) return "Intermediate";
            return "Beginner";
        }
        if (overall >= 60) {
            if (totalRepos >= 3) return "Advanced";
            if (totalRepos >= 2) return "Intermediate";
            return "Beginner";
        }
        if (overall >= 40) {
            if (totalRepos >= 2) return "Intermediate";
            return "Beginner";
        }
        return "Beginner";
    }

    public String experienceLevel(int overall, int totalRepos) {
        return experienceLevel(overall, totalRepos, 0);
    }

    // ---- helpers ----

    private int countSignals(Map<String, Integer> techMap, TechnologyDetector.Capability cap) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : techMap.entrySet()) {
            TechnologyDetector.Capability c = detector.capabilityForDisplay(e.getKey());
            if (c == cap) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    private int toScore(int signalCount) {
        int raw = BASE + signalCount * PER_SIGNAL;
        return Math.min(CAP, raw);
    }

    private double sumWeighted(Map<String, Double> weightedMap, TechnologyDetector.Capability cap) {
        double sum = 0;
        for (Map.Entry<String, Double> e : weightedMap.entrySet()) {
            TechnologyDetector.Capability c = detector.capabilityForDisplay(e.getKey());
            if (c == cap) sum += e.getValue();
        }
        return sum;
    }

    private int toScoreWeighted(double weightedCount) {
        int raw = BASE + (int) Math.round(weightedCount * PER_SIGNAL);
        return Math.min(CAP, raw);
    }

    /**
     * Specialization-aware overall.
     * Sorted descending: top four axis scores weighted.
     * Rewards strengths, recognizes specialization, avoids heavy penalty for missing AI etc.,
     * but not simply max.
     * Formula: 0.4*max +0.3*second +0.2*third +0.1*fourth
     */
    int computeOverall(int backend, int frontend, int database, int ai) {
        int[] scores = {backend, frontend, database, ai};
        Arrays.sort(scores); // ascending
        // now scores[3]=max, [2]=second, [1]=third, [0]=fourth
        double weighted = scores[3] * W1 + scores[2] * W2 + scores[1] * W3 + scores[0] * W4;
        int overall = (int) Math.round(weighted);
        return Math.min(CAP, Math.max(0, overall));
    }

    // Exposed for tests to verify formula
    public int computeOverallForTest(int b, int f, int d, int ai) {
        return computeOverall(b, f, d, ai);
    }
}
