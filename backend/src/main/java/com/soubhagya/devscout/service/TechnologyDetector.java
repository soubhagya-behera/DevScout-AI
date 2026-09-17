package com.soubhagya.devscout.service;

import com.soubhagya.devscout.dto.GitHubRepoDTO;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Stack-agnostic technology detector.
 *
 * Evidence sources (Phase 1 only, no extra GitHub calls):
 *   - repo.name
 *   - repo.description
 *   - repo.language (GitHub primary language field)
 *
 * Normalization: aliases (case-insensitive, synonyms) → canonical display name.
 * Per-repo deduplication: same canonical counted once per repo regardless of
 * multiple alias matches (e.g. spring + spring boot → SPRING_BOOT once).
 *
 * Capability classification is kept separate so scoring can remain stack-agnostic.
 */
@Component
public class TechnologyDetector {

    public enum Capability {
        BACKEND, FRONTEND, DATABASE, AI, DEVOPS
    }

    /**
     * Phase 6: evidence strength per technology per repository.
     * STRONG: topics, README, dependency/manifest evidence, explicit metadata
     * MEDIUM: repository name/description text match
     * WEAK: language fallback only (GitHub primary language field)
     */
    public enum EvidenceStrength {
        STRONG, MEDIUM, WEAK
    }

    private static final String DISPLAY_SPRING_BOOT = "Spring Boot";
    private static final String DISPLAY_JAVA = "Java";
    private static final String DISPLAY_PYTHON = "Python";

    // Canonical → display name (frontend sees display name)
    // Keep existing names compatible: Spring Boot, React, MySQL, JWT, Gemini API, JDBC
    private static final Map<String, String> CANONICAL_DISPLAY = new HashMap<>();

    // alias (lowercase) → canonical key (upper)
    private static final Map<String, String> ALIAS_TO_CANONICAL = new HashMap<>();

    // canonical → capability (one per tech; NEXTJS primary FRONTEND, PYTHON as BACKEND only)
    private static final Map<String, Capability> CANONICAL_CAPABILITY = new HashMap<>();

    // canonical keys that need word-boundary matching (short / ambiguous)
    private static final Set<String> WORD_BOUNDARY_ALIASES = new HashSet<>();

    static {
        // ---------- helper to register ----------
        // backend: Java ecosystem
        register("SPRING_BOOT", DISPLAY_SPRING_BOOT, Capability.BACKEND,
                List.of("spring boot", "springboot", "spring-boot"));
        register("SPRING", "Spring", Capability.BACKEND,
                List.of("spring framework", "spring mvc"));
        register("JAVA", DISPLAY_JAVA, Capability.BACKEND,
                List.of("java"));
        register("HIBERNATE", "Hibernate", Capability.BACKEND,
                List.of("hibernate"));
        register("JPA", "JPA", Capability.BACKEND,
                List.of("jpa"));
        register("JDBC", "JDBC", Capability.BACKEND,
                List.of("jdbc"));

        // backend: Python
        register("PYTHON", DISPLAY_PYTHON, Capability.BACKEND,
                List.of("python"));
        register("DJANGO", "Django", Capability.BACKEND,
                List.of("django"));
        register("FASTAPI", "FastAPI", Capability.BACKEND,
                List.of("fastapi", "fast api", "fast-api"));
        register("FLASK", "Flask", Capability.BACKEND,
                List.of("flask"));

        // backend: Node/JS
        register("NODE", "Node.js", Capability.BACKEND,
                List.of("node.js", "nodejs", "node js", "node"));
        register("EXPRESS", "Express", Capability.BACKEND,
                List.of("express", "express.js", "expressjs"));
        register("NESTJS", "NestJS", Capability.BACKEND,
                List.of("nestjs", "nest.js", "nest js"));
        // NEXTJS primary frontend (also counts as frontend); keep one canonical
        register("NEXTJS", "Next.js", Capability.FRONTEND,
                List.of("next.js", "nextjs", "next js"));

        // backend: .NET
        register("CSHARP", "C#", Capability.BACKEND,
                List.of("c#", "csharp", "c sharp"));
        register("DOTNET", ".NET", Capability.BACKEND,
                List.of(".net", "dotnet"));
        register("ASPNET", "ASP.NET Core", Capability.BACKEND,
                List.of("asp.net core", "asp.net", "aspnet core", "aspnet"));

        // backend: Go
        register("GO", "Go", Capability.BACKEND,
                List.of("go", "golang"));
        register("GIN", "Gin", Capability.BACKEND,
                List.of("gin"));
        register("FIBER", "Fiber", Capability.BACKEND,
                List.of("fiber"));

        // backend: PHP
        register("PHP", "PHP", Capability.BACKEND,
                List.of("php"));
        register("LARAVEL", "Laravel", Capability.BACKEND,
                List.of("laravel"));
        register("SYMFONY", "Symfony", Capability.BACKEND,
                List.of("symfony"));

        // backend: Ruby
        register("RUBY", "Ruby", Capability.BACKEND,
                List.of("ruby"));
        register("RAILS", "Rails", Capability.BACKEND,
                List.of("rails", "ruby on rails"));

        // frontend
        register("JAVASCRIPT", "JavaScript", Capability.FRONTEND,
                List.of("javascript", "js"));
        register("TYPESCRIPT", "TypeScript", Capability.FRONTEND,
                List.of("typescript", "ts"));
        register("REACT", "React", Capability.FRONTEND,
                List.of("react", "react.js", "reactjs"));
        register("ANGULAR", "Angular", Capability.FRONTEND,
                List.of("angular"));
        register("VUE", "Vue", Capability.FRONTEND,
                List.of("vue", "vue.js", "vuejs"));
        register("SVELTE", "Svelte", Capability.FRONTEND,
                List.of("svelte"));
        register("HTML", "HTML", Capability.FRONTEND,
                List.of("html"));
        register("CSS", "CSS", Capability.FRONTEND,
                List.of("css"));

        // generic backend auth
        register("JWT", "JWT", Capability.BACKEND,
                List.of("jwt"));

        // database relational
        register("POSTGRESQL", "PostgreSQL", Capability.DATABASE,
                List.of("postgresql", "postgres", "psql"));
        register("MYSQL", "MySQL", Capability.DATABASE,
                List.of("mysql"));
        register("MARIADB", "MariaDB", Capability.DATABASE,
                List.of("mariadb"));
        register("ORACLE", "Oracle", Capability.DATABASE,
                List.of("oracle db", "oracle database"));
        register("SQLSERVER", "SQL Server", Capability.DATABASE,
                List.of("sql server", "sqlserver", "mssql"));
        register("SQLITE", "SQLite", Capability.DATABASE,
                List.of("sqlite"));

        // database nosql
        register("MONGODB", "MongoDB", Capability.DATABASE,
                List.of("mongodb", "mongo db", "mongo"));
        register("REDIS", "Redis", Capability.DATABASE,
                List.of("redis"));
        register("CASSANDRA", "Cassandra", Capability.DATABASE,
                List.of("cassandra"));
        register("DYNAMODB", "DynamoDB", Capability.DATABASE,
                List.of("dynamodb", "dynamo db"));

        // ai / data
        register("TENSORFLOW", "TensorFlow", Capability.AI,
                List.of("tensorflow"));
        register("PYTORCH", "PyTorch", Capability.AI,
                List.of("pytorch", "torch"));
        register("SCIKIT", "scikit-learn", Capability.AI,
                List.of("scikit-learn", "scikit learn", "sklearn"));
        register("PANDAS", "Pandas", Capability.AI,
                List.of("pandas"));
        register("NUMPY", "NumPy", Capability.AI,
                List.of("numpy"));
        register("OPENAI", "OpenAI", Capability.AI,
                List.of("openai"));
        register("GEMINI_API", "Gemini API", Capability.AI,
                List.of("gemini"));
        register("LANGCHAIN", "LangChain", Capability.AI,
                List.of("langchain"));

        // devops / cloud
        register("DOCKER", "Docker", Capability.DEVOPS,
                List.of("docker"));
        register("KUBERNETES", "Kubernetes", Capability.DEVOPS,
                List.of("kubernetes", "k8s"));
        register("AWS", "AWS", Capability.DEVOPS,
                List.of("aws", "amazon web services"));
        register("AZURE", "Azure", Capability.DEVOPS,
                List.of("azure"));
        register("GCP", "GCP", Capability.DEVOPS,
                List.of("gcp", "google cloud"));
        register("TERRAFORM", "Terraform", Capability.DEVOPS,
                List.of("terraform"));
        register("GITHUB_ACTIONS", "GitHub Actions", Capability.DEVOPS,
                List.of("github actions", "gh actions"));
        register("CICD", "CI/CD", Capability.DEVOPS,
                List.of("ci/cd", "cicd", "ci cd"));

        // Special aliases that would self-match incorrectly handled via word boundaries
        WORD_BOUNDARY_ALIASES.add("java");
        WORD_BOUNDARY_ALIASES.add("go");
        WORD_BOUNDARY_ALIASES.add("golang");
        WORD_BOUNDARY_ALIASES.add("js");
        WORD_BOUNDARY_ALIASES.add("ts");
        WORD_BOUNDARY_ALIASES.add("css");
        WORD_BOUNDARY_ALIASES.add("html");
        WORD_BOUNDARY_ALIASES.add("ruby");
        WORD_BOUNDARY_ALIASES.add("php");
        WORD_BOUNDARY_ALIASES.add("vue");
        WORD_BOUNDARY_ALIASES.add("gin");
        WORD_BOUNDARY_ALIASES.add("fiber");
        WORD_BOUNDARY_ALIASES.add("rails");
        WORD_BOUNDARY_ALIASES.add("jwt");
        WORD_BOUNDARY_ALIASES.add("node");
    }

    private static void register(String canonical, String display, Capability cap, List<String> aliases) {
        CANONICAL_DISPLAY.put(canonical, display);
        CANONICAL_CAPABILITY.put(canonical, cap);
        for (String alias : aliases) {
            String key = alias.toLowerCase(Locale.ROOT);
            // map both alias→canonical; if overlapping alias (e.g. "spring boot" vs "spring") keep first but allow override? dedup via canonical
            // For overlapping synonyms that should be same canonical (node variants) already same canonical, fine.
            // For "spring" generic vs spring boot, keep distinct canonicals but per-repo dedup will avoid double count via same text containing both?
            // We keep mapping as is; detection will handle word-boundary separately.
            ALIAS_TO_CANONICAL.putIfAbsent(key, canonical);
            // Also ensure specific aliases map correctly when canonical appears multiple times
            // If alias already exists for different canonical, keep first—rare and intentional (e.g., "spring" generic)
            // But for spring boot variants we want them mapped to SPRING_BOOT
        }
        // Ensure canonical itself as alias for language-based fallback (lowercase display)
        // Not needed—language mapping handled separately
    }

    /**
     * Detect technologies from already-available repo metadata + Phase 3 enrichment.
     * Returns displayName → occurrence count (repos containing it).
     * Evidence sources: name, description, language, topics, readmeContent, dependencyEvidence
     * Per-repo dedup preserved: one canonical per repo regardless of multiple sources mentioning it.
     */
    public Map<String, Integer> detect(List<GitHubRepoDTO> repos) {
        Map<String, Integer> techMap = new HashMap<>();
        if (repos == null || repos.isEmpty()) return techMap;

        for (GitHubRepoDTO repo : repos) {
            Set<String> foundThisRepo = new HashSet<>();

            // 1) evidence text = name + description + topics + readme + dependency
            String name = repo.getName() != null ? repo.getName() : "";
            String desc = repo.getDescription() != null ? repo.getDescription() : "";
            StringBuilder evidenceBuilder = new StringBuilder();
            evidenceBuilder.append(name).append(" ").append(desc).append(" ");
            if (repo.getTopics() != null && !repo.getTopics().isEmpty()) {
                evidenceBuilder.append(String.join(" ", repo.getTopics())).append(" ");
            }
            if (repo.getReadmeContent() != null && !repo.getReadmeContent().isBlank()) {
                // truncate already done at fetch, but ensure lowercasing
                evidenceBuilder.append(repo.getReadmeContent()).append(" ");
            }
            if (repo.getDependencyEvidence() != null && !repo.getDependencyEvidence().isBlank()) {
                evidenceBuilder.append(repo.getDependencyEvidence()).append(" ");
            }
            String evidence = evidenceBuilder.toString().toLowerCase(Locale.ROOT);

            // 2) language fallback: GitHub language field → canonical
            String lang = repo.getLanguage();
            if (lang != null) {
                String langLower = lang.toLowerCase(Locale.ROOT).trim();
                String langCanonical = languageToCanonical(langLower);
                if (langCanonical != null) {
                    foundThisRepo.add(langCanonical);
                }
            }

            // 3) alias scanning
            for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
                String alias = entry.getKey();
                String canonical = entry.getValue();
                if (foundThisRepo.contains(canonical)) {
                    // already found via language or previous alias for same canonical—skip extra check
                    // Still need to count once, so continue (but keep dedup per repo)
                }
                if (containsAlias(evidence, alias)) {
                    foundThisRepo.add(canonical);
                }
            }

            // Special handling: SPRING vs SPRING_BOOT disambiguation
            if (foundThisRepo.contains("SPRING_BOOT") && foundThisRepo.contains("SPRING")) {
                foundThisRepo.remove("SPRING");
            }

            // Increment per-repo counts
            for (String canonical : foundThisRepo) {
                String display = CANONICAL_DISPLAY.get(canonical);
                if (display == null) display = canonical;
                techMap.put(display, techMap.getOrDefault(display, 0) + 1);
            }
        }
        return techMap;
    }

    /**
     * Phase 6: detect with evidence strength per technology.
     * Returns displayName → strongest EvidenceStrength observed across repos.
     * Strong = topics/readme/dependency, Medium = name/description, Weak = language only.
     * Per-repo deduplication preserved, strongest source wins per repo, then max across repos.
     */
    public Map<String, EvidenceStrength> detectStrongestStrength(List<GitHubRepoDTO> repos) {
        Map<String, EvidenceStrength> strongest = new HashMap<>();
        if (repos == null || repos.isEmpty()) return strongest;
        for (GitHubRepoDTO repo : repos) {
            Map<String, EvidenceStrength> perRepo = detectStrengthPerRepo(repo);
            for (Map.Entry<String, EvidenceStrength> e : perRepo.entrySet()) {
                String display = e.getKey();
                EvidenceStrength cur = e.getValue();
                EvidenceStrength prev = strongest.get(display);
                if (prev == null || cur.ordinal() < prev.ordinal()) {
                    // STRONG(0) < MEDIUM(1) < WEAK(2) → lower ordinal = stronger
                    strongest.put(display, cur);
                }
            }
        }
        return strongest;
    }

    /**
     * Phase 6: weighted count where strong=1.0, medium=0.75, weak=0.35.
     * Returns displayName → sum(weights) across repos (e.g., 2 repos one strong one weak => 1.35).
     * Keeps stack-neutral weighting.
     */
    public Map<String, Double> detectWeightedCount(List<GitHubRepoDTO> repos) {
        Map<String, Double> weighted = new HashMap<>();
        if (repos == null || repos.isEmpty()) return weighted;
        for (GitHubRepoDTO repo : repos) {
            Map<String, EvidenceStrength> perRepo = detectStrengthPerRepo(repo);
            for (Map.Entry<String, EvidenceStrength> e : perRepo.entrySet()) {
                String display = e.getKey();
                double w = weightForStrength(e.getValue());
                weighted.put(display, weighted.getOrDefault(display, 0.0) + w);
            }
        }
        return weighted;
    }

    public double weightForStrength(EvidenceStrength s) {
        return switch (s) {
            case STRONG -> 1.0;
            case MEDIUM -> 1.0;
            case WEAK -> 0.4;
        };
    }

    /**
     * Per-repo strength detection. Returns display → strength for this single repo.
     */
    Map<String, EvidenceStrength> detectStrengthPerRepo(GitHubRepoDTO repo) {
        Map<String, EvidenceStrength> result = new HashMap<>();
        if (repo == null) return result;

        String name = repo.getName() != null ? repo.getName() : "";
        String desc = repo.getDescription() != null ? repo.getDescription() : "";
        String mediumEvidence = (name + " " + desc).toLowerCase(Locale.ROOT);

        StringBuilder strongBuilder = new StringBuilder();
        if (repo.getTopics() != null && !repo.getTopics().isEmpty()) {
            strongBuilder.append(String.join(" ", repo.getTopics())).append(" ");
        }
        if (repo.getReadmeContent() != null && !repo.getReadmeContent().isBlank()) {
            strongBuilder.append(repo.getReadmeContent()).append(" ");
        }
        if (repo.getDependencyEvidence() != null && !repo.getDependencyEvidence().isBlank()) {
            strongBuilder.append(repo.getDependencyEvidence()).append(" ");
        }
        String strongEvidence = strongBuilder.toString().toLowerCase(Locale.ROOT);

        // check strong then medium
        Set<String> foundStrong = new HashSet<>();
        Set<String> foundMedium = new HashSet<>();

        for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
            String alias = entry.getKey();
            String canonical = entry.getValue();
            if (containsAlias(strongEvidence, alias)) {
                foundStrong.add(canonical);
            } else if (containsAlias(mediumEvidence, alias)) {
                foundMedium.add(canonical);
            }
        }
        // handle spring disambiguation in both sets
        if (foundStrong.contains("SPRING_BOOT") && foundStrong.contains("SPRING")) foundStrong.remove("SPRING");
        if (foundMedium.contains("SPRING_BOOT") && foundMedium.contains("SPRING")) foundMedium.remove("SPRING");
        // remove medium duplicates that are already strong
        foundMedium.removeAll(foundStrong);

        // build display map with strength
        for (String canonical : foundStrong) {
            String display = CANONICAL_DISPLAY.getOrDefault(canonical, canonical);
            result.put(display, EvidenceStrength.STRONG);
        }
        for (String canonical : foundMedium) {
            String display = CANONICAL_DISPLAY.getOrDefault(canonical, canonical);
            result.put(display, EvidenceStrength.MEDIUM);
        }
        // language fallback -> WEAK only if not already detected stronger
        String lang = repo.getLanguage();
        if (lang != null) {
            String langLower = lang.toLowerCase(Locale.ROOT).trim();
            String langCanonical = languageToCanonical(langLower);
            if (langCanonical != null) {
                String display = CANONICAL_DISPLAY.getOrDefault(langCanonical, langCanonical);
                if (!result.containsKey(display)) {
                    result.put(display, EvidenceStrength.WEAK);
                }
            }
        }
        // final spring_boot disambiguation across strengths (if both via different sources, strong wins)
        if (result.containsKey("Spring Boot") && result.containsKey("Spring")) {
            result.remove("Spring");
        }
        return result;
    }

    /**
     * For tests / scoring: expose capability of display name (reverse lookup).
     */
    public Capability capabilityForDisplay(String display) {
        for (Map.Entry<String, String> e : CANONICAL_DISPLAY.entrySet()) {
            if (e.getValue().equals(display)) {
                return CANONICAL_CAPABILITY.get(e.getKey());
            }
        }
        return null;
    }

    public Capability capabilityForCanonical(String canonical) {
        return CANONICAL_CAPABILITY.get(canonical);
    }

    public String displayForCanonical(String canonical) {
        return CANONICAL_DISPLAY.get(canonical);
    }

    // language string (lower) → canonical key
    private String languageToCanonical(String langLower) {
        return switch (langLower) {
            case "java" -> "JAVA";
            case "python" -> "PYTHON";
            case "javascript" -> "JAVASCRIPT";
            case "typescript" -> "TYPESCRIPT";
            case "c#" , "csharp", "c sharp" -> "CSHARP";
            case "go", "golang" -> "GO";
            case "php" -> "PHP";
            case "ruby" -> "RUBY";
            case "html" -> "HTML";
            case "css" -> "CSS";
            case "c++", "c", "kotlin", "rust", "swift", "scala" -> null; // unknown -> no mapping, gracefully ignored
            default -> null;
        };
    }

    private boolean containsAlias(String evidenceLower, String aliasLower) {
        if (evidenceLower == null || evidenceLower.isEmpty() || aliasLower.isEmpty()) return false;
        if (!evidenceLower.contains(aliasLower)) return false;

        // For ambiguous short tokens, require word boundaries
        if (WORD_BOUNDARY_ALIASES.contains(aliasLower)) {
            // Use regex \b alias \b, escaping alias
            String escaped = Pattern.quote(aliasLower);
            Pattern p = Pattern.compile("\\b" + escaped + "\\b");
            return p.matcher(evidenceLower).find();
        }
        // For multi-word aliases, simple contains is sufficient and more tolerant
        // But ensure not matching "java" inside "javascript" when alias is "java"
        // Already handled via WORD_BOUNDARY_ALIASES
        return true;
    }
}
