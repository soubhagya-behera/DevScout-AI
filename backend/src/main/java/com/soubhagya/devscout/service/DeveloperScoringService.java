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
     * Phase 6: evidence-weighted scoring.
     * Weighted count uses per-repo strength: STRONG=1.0, MEDIUM=0.75, WEAK=0.35.
     * This ensures language-only evidence (WEAK) scores lower than manifest/README evidence.
     * Formula: score = min(100, BASE + round(weightedCount * PER_SIGNAL)), BASE=20, PER_SIGNAL=15.
     * Deterministic, stack-neutral, bounds 0-100, explainable.
     * Does not penalize repos without manifests: they still contribute via MEDIUM/WEAK.
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
     * Phase 6: score directly from repo list using per-repo strength weighting.
     */
    public DeveloperScoreDTO scoreFromRepos(List<com.soubhagya.devscout.dto.GitHubRepoDTO> repos) {
        if (repos == null || repos.isEmpty()) return score(Map.of());
        Map<String, Double> weighted = detector.detectWeightedCount(repos);
        // fallback to integer count if weighted is empty due to no evidence (keeps empty profile at BASE)
        if (weighted.isEmpty()) return score(Map.of());
        return scoreWeighted(weighted);
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
