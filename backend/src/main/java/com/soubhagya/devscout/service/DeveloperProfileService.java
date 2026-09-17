package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Phase 2: Evidence-based developer profile & experience assessment.
 * Deterministic, no Gemini, uses only data already available in GitHubService flow.
 *
 * Signals used:
 *  - technology map (display->repos, from TechnologyDetector)
 *  - capability signals (BACKEND/FRONTEND/DATABASE/AI counts + scores)
 *  - languages map
 *  - repos list: name, description, language, stars
 *  - derived: meaningfulRepositories, totalStars, distinctTechnologies, breadth, depth
 *
 * Does NOT infer employment years, salary, hiring probability — only GitHub evidence strength.
 */
@Component
public class DeveloperProfileService {

    private static final int MEANINGFUL_DESC_MIN = 15;
    private static final int MEANINGFUL_DESC_LONG = 30;
    private static final int CAPABILITY_PRESENT_THRESHOLD = 35; // 20 + 15*1
    // Phase 3: activity windows
    static final int RECENT_DAYS = 90;
    static final int ACTIVE_DAYS = 365;

    private final TechnologyDetector detector;

    public DeveloperProfileService(TechnologyDetector detector) {
        this.detector = detector;
    }

    public DeveloperProfileAssessment assess(
            List<GitHubRepoDTO> repos,
            Map<String, Integer> technologies,
            Map<String, Integer> languages,
            DeveloperScoreDTO scores
    ) {
        if (technologies == null) technologies = Collections.emptyMap();
        if (languages == null) languages = Collections.emptyMap();
        if (repos == null) repos = Collections.emptyList();

        int totalRepos = repos.size();
        int totalStars = repos.stream().mapToInt(GitHubRepoDTO::getStars).sum();
        int distinctTechs = technologies.size();

        int meaningfulRepos = countMeaningfulRepositories(repos);

        // Phase 10.1: capability signals = distinct repositories per capability (per-repo max, not per-tech sum)
        int backendCount = countDistinctRepos(repos, TechnologyDetector.Capability.BACKEND);
        int frontendCount = countDistinctRepos(repos, TechnologyDetector.Capability.FRONTEND);
        int databaseCount = countDistinctRepos(repos, TechnologyDetector.Capability.DATABASE);
        int aiCount = countDistinctRepos(repos, TechnologyDetector.Capability.AI);

        int backendScore = scores != null ? scores.getBackendScore() : 20;
        int frontendScore = scores != null ? scores.getFrontendScore() : 20;
        int databaseScore = scores != null ? scores.getDatabaseScore() : 20;
        int aiScore = scores != null ? scores.getAiScore() : 20;
        int overall = scores != null ? scores.getOverallScore() : 20;

        int breadth = countBreadth(backendScore, frontendScore, databaseScore, aiScore);
        int depth = Math.max(Math.max(backendScore, frontendScore), Math.max(databaseScore, aiScore));
        String specialization = specialization(backendScore, frontendScore, databaseScore, aiScore);

        DeveloperProfileType profileType = classifyProfile(
                technologies, backendScore, frontendScore, databaseScore, aiScore,
                backendCount, frontendCount, aiCount, meaningfulRepos, totalRepos
        );

        Confidence confidence = assessConfidence(totalRepos, meaningfulRepos, distinctTechs, breadth, overall, repos);

        // Experience uses richer signals but preserves label contract
        String experienceLevel = assessExperience(overall, meaningfulRepos, totalRepos, totalStars, breadth, depth, distinctTechs);
        String experienceEvidence = String.format(
                "overall=%d meaningful=%d/%d stars=%d breadth=%d depth=%d techs=%d",
                overall, meaningfulRepos, totalRepos, totalStars, breadth, depth, distinctTechs
        );

        Map<String,Integer> capabilitySignals = new HashMap<>();
        capabilitySignals.put("BACKEND", backendCount);
        capabilitySignals.put("FRONTEND", frontendCount);
        capabilitySignals.put("DATABASE", databaseCount);
        capabilitySignals.put("AI", aiCount);

        String evidenceSummary = String.format(
                "profile=%s backend=%d(%d) frontend=%d(%d) db=%d ai=%d(%d) langs=%s",
                profileType.getDisplay(), backendScore, backendCount, frontendScore, frontendCount,
                databaseScore, aiScore, aiCount, languages.keySet()
        );

        DeveloperProfileAssessment a = new DeveloperProfileAssessment();
        a.setProfileType(profileType);
        a.setConfidence(confidence);
        a.setSpecialization(specialization);
        a.setBreadth(breadth);
        a.setDepth(depth);
        a.setExperienceLevel(experienceLevel);
        a.setExperienceEvidence(experienceEvidence);
        a.setMeaningfulRepositories(meaningfulRepos);
        a.setTotalRepositories(totalRepos);
        a.setTotalStars(totalStars);
        a.setDistinctTechnologies(distinctTechs);
        a.setCapabilitySignals(capabilitySignals);
        a.setEvidenceSummary(evidenceSummary);
        return a;
    }

    // overload without languages (backward)
    public DeveloperProfileAssessment assess(
            List<GitHubRepoDTO> repos,
            Map<String, Integer> technologies,
            DeveloperScoreDTO scores
    ) {
        return assess(repos, technologies, Collections.emptyMap(), scores);
    }

    // ---- meaningful repos (Phase 3: uses fork/archived/size/updated_at) ----

    int countMeaningfulRepositories(List<GitHubRepoDTO> repos) {
        int count = 0;
        for (GitHubRepoDTO repo : repos) {
            if (isMeaningful(repo)) count++;
        }
        return count;
    }

    /**
     * Deterministic Phase 3 meaningful policy:
     * - size==0 && !hasTech && stars==0 → EXCLUDED (empty/tutorial)
     * - fork small without tech/stars → WEAK (not meaningful)
     * - archived with tech → still meaningful (demonstrates ability)
     * - hasTech always meaningful (even for fork/archived)
     * - stars alone meaningful
     * - hasDesc+hasLang+size>0 → meaningful
     */
    boolean isMeaningful(GitHubRepoDTO repo) {
        if (repo == null) return false;
        boolean hasTech = !detector.detect(List.of(repo)).isEmpty();
        String desc = repo.getDescription();
        boolean hasDesc = desc != null && desc.trim().length() >= MEANINGFUL_DESC_MIN;
        boolean hasLongDesc = desc != null && desc.trim().length() >= MEANINGFUL_DESC_LONG;
        boolean hasLang = repo.getLanguage() != null && !repo.getLanguage().isBlank();
        boolean hasStars = repo.getStars() > 0;
        int size = repo.getSize();
        boolean isFork = repo.isFork();
        boolean isArchived = repo.isArchived();

        // empty repository: size 0, no tech, no stars, no description
        if (size == 0 && !hasTech && !hasStars && !hasDesc) return false;

        // tech evidence always meaningful, even for fork/archived (demonstrates skill)
        if (hasTech) return true;

        // stars imply community interest
        if (hasStars) return true;

        // fork small without tech: require stronger evidence
        if (isFork && size < 50 && !hasLongDesc) return false;

        // archived without tech: require description+language
        if (isArchived && !(hasDesc && hasLang)) return false;

        // description + language + non-empty size → meaningful original work
        if (hasDesc && hasLang && size > 0) return true;
        if (hasDesc && hasLang && size == 0 && hasLongDesc) return true; // long desc compensates size 0

        return false;
    }

    // ---- activity / recency (Phase 3: uses pushed_at/updated_at, no commit API) ----

    String classifyRecency(GitHubRepoDTO repo) {
        String ts = repo.getPushed_at() != null ? repo.getPushed_at() : repo.getUpdated_at();
        if (ts == null || ts.isBlank()) return "UNKNOWN";
        try {
            java.time.Instant instant = java.time.Instant.parse(ts);
            long days = java.time.Duration.between(instant, java.time.Instant.now()).toDays();
            if (days <= RECENT_DAYS) return "RECENT";
            if (days <= ACTIVE_DAYS) return "ACTIVE";
            return "STALE";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    // Build repository evidence list (used for summary, not exposed in full)
    java.util.List<RepositoryEvidence> buildEvidenceList(List<GitHubRepoDTO> repos) {
        java.util.List<RepositoryEvidence> list = new java.util.ArrayList<>();
        for (GitHubRepoDTO repo : repos) {
            RepositoryEvidence ev = new RepositoryEvidence();
            ev.setName(repo.getName());
            ev.setFork(repo.isFork());
            ev.setArchived(repo.isArchived());
            ev.setSize(repo.getSize());
            ev.setUpdatedAt(repo.getUpdated_at());
            ev.setPushedAt(repo.getPushed_at());
            ev.setStars(repo.getStars());
            ev.setLanguage(repo.getLanguage());
            ev.setRecency(classifyRecency(repo));
            ev.setMeaningful(isMeaningful(repo));
            // simple quality score: tech present 50 + size>0 20 + stars>0 20 + recent 10
            int qs = 0;
            if (!detector.detect(List.of(repo)).isEmpty()) qs += 50;
            if (repo.getSize() > 0) qs += 20;
            if (repo.getStars() > 0) qs += 20;
            if ("RECENT".equals(ev.getRecency())) qs += 10;
            ev.setQualityScore(Math.min(100, qs));
            ev.setEvidenceSummary(String.format("%s fork=%b archived=%b size=%d recency=%s meaningful=%b",
                    repo.getName(), ev.isFork(), ev.isArchived(), ev.getSize(), ev.getRecency(), ev.isMeaningful()));
            list.add(ev);
        }
        return list;
    }

    // ---- profile classification ----

    DeveloperProfileType classifyProfile(
            Map<String,Integer> techs,
            int backendScore, int frontendScore, int databaseScore, int aiScore,
            int backendCount, int frontendCount, int aiCount,
            int meaningfulRepos, int totalRepos
    ) {
        if (totalRepos == 0 || meaningfulRepos == 0) {
            return DeveloperProfileType.UNKNOWN;
        }

        boolean hasPandas = techs.containsKey("Pandas");
        boolean hasNumpy = techs.containsKey("NumPy");
        boolean hasSklearn = techs.containsKey("scikit-learn");
        boolean hasMlLib = techs.containsKey("PyTorch") || techs.containsKey("TensorFlow") || techs.containsKey("scikit-learn");
        // AI/ML requires actual ML engineering (PyTorch/TensorFlow/scikit-learn), not just API integration
        if (hasMlLib && aiScore >= 30) {
            return DeveloperProfileType.AI_ML_DEVELOPER;
        }
        if (!hasMlLib && (hasPandas || hasNumpy) && aiScore >= 30 && aiCount >= 2) {
            if (backendScore >= 30) return DeveloperProfileType.DATA_BACKEND_DEVELOPER;
            return DeveloperProfileType.AI_ML_DEVELOPER;
        }

        // Full stack: both backend and frontend meaningful (calibrated for diminishing+quality)
        boolean backendStrong = backendScore >= 30;
        boolean frontendStrong = frontendScore >= 30;
        if (backendStrong && frontendStrong) {
            return DeveloperProfileType.FULL_STACK_DEVELOPER;
        }
        // Full-stack backend-heavy with database
        if (backendScore >= 40 && frontendScore >= 28 && databaseScore >= 28) {
            if (frontendCount >= 1) return DeveloperProfileType.FULL_STACK_DEVELOPER;
        }

        // Frontend specialist
        if (frontendScore >= 45 && frontendScore > backendScore + 10) {
            return DeveloperProfileType.FRONTEND_DEVELOPER;
        }
        if (frontendScore >= 35 && frontendCount >= 2 && backendScore < 30) {
            return DeveloperProfileType.FRONTEND_DEVELOPER;
        }

        // Backend specialist (covers Java/Python/Go/.NET/PHP/Ruby)
        if (backendScore >= 40 && backendScore > frontendScore + 8) {
            return DeveloperProfileType.BACKEND_DEVELOPER;
        }
        if (backendScore >= 35 && backendCount >= 1 && frontendScore < 35) {
            return DeveloperProfileType.BACKEND_DEVELOPER;
        }

        // Fallbacks
        if (frontendScore >= 30 && frontendScore >= backendScore) {
            return DeveloperProfileType.FRONTEND_DEVELOPER;
        }
        if (backendScore >= 30) {
            return DeveloperProfileType.BACKEND_DEVELOPER;
        }
        if (frontendScore >= 28 || backendScore >= 28 || aiScore >= 28) {
            return DeveloperProfileType.GENERAL_SOFTWARE_DEVELOPER;
        }
        return DeveloperProfileType.UNKNOWN;
    }

    Confidence assessConfidence(int totalRepos, int meaningfulRepos, int distinctTechs, int breadth, int overall) {
        return assessConfidence(totalRepos, meaningfulRepos, distinctTechs, breadth, overall, Collections.emptyList());
    }

    Confidence assessConfidence(int totalRepos, int meaningfulRepos, int distinctTechs, int breadth, int overall, List<GitHubRepoDTO> repos) {
        if (totalRepos == 0 || meaningfulRepos == 0) return Confidence.LOW;
        Confidence base;
        if (totalRepos >= 8 && meaningfulRepos >= 5 && distinctTechs >= 4 && breadth >= 2 && overall >= 50) {
            base = Confidence.HIGH;
        } else if (totalRepos >= 4 && meaningfulRepos >= 3 && (distinctTechs >= 3 || breadth >= 2) && overall >= 35) {
            base = Confidence.MEDIUM;
        } else if (totalRepos >= 2 && meaningfulRepos >= 2 && distinctTechs >= 2) {
            base = Confidence.MEDIUM;
        } else {
            return Confidence.LOW;
        }
        // Minimal recency guard for HIGH: stale/fork-heavy but entirely old evidence should not stay HIGH.
        // Historical evidence remains valid, so we only demote HIGH when evidence is explicitly stale with no recent activity.
        if (base == Confidence.HIGH && repos != null && !repos.isEmpty()) {
            long recent = repos.stream().filter(r -> "RECENT".equals(classifyRecency(r))).count();
            long active = repos.stream().filter(r -> "ACTIVE".equals(classifyRecency(r))).count();
            long stale = repos.stream().filter(r -> "STALE".equals(classifyRecency(r))).count();
            long known = recent + active + stale;
            // Only demote if we have known recency and it is entirely stale (or only 0-1 active)
            if (known > 0 && recent == 0 && active < 2 && stale > 0) {
                return Confidence.MEDIUM;
            }
        }
        return base;
    }

    /**
     * Phase 6: Experience labels describe GitHub evidence strength/depth, NOT employment years.
     * Beginner=limited evidence, Intermediate=some meaningful repos, Advanced=breadth+depth, Expert=strong multi-dimensional evidence.
     * Repo count alone, stars alone, language count alone cannot yield Expert.
     */
    String assessExperience(int overall, int meaningfulRepos, int totalRepos, int totalStars, int breadth, int depth, int distinctTechs) {
        if (totalRepos <= 0 || meaningfulRepos == 0) return "Beginner";

        // Expert needs strong evidence across multiple dimensions, not just stars
        if (overall >= 75 && meaningfulRepos >= 5 && totalRepos >= 8 && breadth >= 2 && distinctTechs >= 4 && depth >= 70) {
            // stars help but not required if other signals very strong; allow 0 stars if overall very high
            if (totalStars >= 5 || overall >= 80) return "Expert";
            // still expert if deep specialization without many stars? keep Expert if depth high
            if (depth >= 85 && meaningfulRepos >= 6) return "Expert";
        }
        if (overall >= 70 && meaningfulRepos >= 4 && totalRepos >= 6 && (breadth >= 2 || depth >= 75)) {
            return "Advanced";
        }
        if (overall >= 60 && meaningfulRepos >= 3 && totalRepos >= 4 && (breadth >= 2 || depth >= 60)) {
            return "Advanced";
        }
        if (overall >= 45 && meaningfulRepos >= 2 && totalRepos >= 2) {
            return "Intermediate";
        }
        if (overall >= 30 && meaningfulRepos >= 1) {
            // very sparse but some signal
            if (totalRepos >= 2 && meaningfulRepos >= 1) return "Beginner";
            return "Beginner";
        }
        return "Beginner";
    }

    // keep backward compatible simple experience for tests that use old thresholds
    String specialization(int backend, int frontend, int database, int ai) {
        int max = Math.max(Math.max(backend, frontend), Math.max(database, ai));
        if (max == backend && backend == frontend) return "FULL_STACK";
        if (max == backend) return "BACKEND";
        if (max == frontend) return "FRONTEND";
        if (max == database) return "DATABASE";
        if (max == ai) return "AI";
        return "GENERAL";
    }

    private int countCapability(Map<String,Integer> techs, TechnologyDetector.Capability cap) {
        int sum = 0;
        for (Map.Entry<String,Integer> e : techs.entrySet()) {
            TechnologyDetector.Capability c = detector.capabilityForDisplay(e.getKey());
            if (c == cap) sum += e.getValue();
        }
        return sum;
    }

    // Phase 10.1: distinct repositories per capability (per-repo max)
    int countDistinctRepos(List<GitHubRepoDTO> repos, TechnologyDetector.Capability cap) {
        if (repos == null || repos.isEmpty()) return 0;
        int count = 0;
        for (GitHubRepoDTO repo : repos) {
            Map<String, TechnologyDetector.EvidenceStrength> perRepo = detector.detectStrengthPerRepo(repo);
            for (Map.Entry<String, TechnologyDetector.EvidenceStrength> e : perRepo.entrySet()) {
                if (detector.capabilityForDisplay(e.getKey()) == cap) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private int countBreadth(int b,int f,int d,int ai) {
        int c=0;
        if (b >= CAPABILITY_PRESENT_THRESHOLD) c++;
        if (f >= CAPABILITY_PRESENT_THRESHOLD) c++;
        if (d >= CAPABILITY_PRESENT_THRESHOLD) c++;
        if (ai >= CAPABILITY_PRESENT_THRESHOLD) c++;
        return c;
    }
}
