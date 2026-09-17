# DevScout AI

AI-powered GitHub developer analysis that turns public repository evidence into structured technical insights, deterministic skill scores, and qualitative hiring context.

> Enter a GitHub username → get a unified developer report with technologies, engineering signals, and AI-generated recruiter notes in one request.

Live: **https://dev-scout-ai.vercel.app** · API: **https://devscout-ai-backend.onrender.com/api/github**

---

## Overview

DevScout AI fetches public GitHub repository data for a username and produces a single `FinalReportDTO` without requiring the frontend to orchestrate multiple calls. Repository metadata is analyzed locally for language/technology signals, capability scores, and evidence-based profile classification. A single Gemini call then generates qualitative insights (level, strengths, improvements, hiring recommendation). Results are cached in-memory and served via one unified REST endpoint.

The project intentionally avoids external infrastructure (no Redis, database, queue, or microservices) to remain simple to deploy on Render + Vercel.

```
GitHub Profile
      ↓
GET /api/github/report/{username}
      ↓
GitHubService → GitHub REST API (repos?per_page=100)
      ↓
TechnologyDetector → DeveloperScoringService → DeveloperProfileService
      ↓
GeminiService (one logical call, stack-neutral fallback)
      ↓
FinalReportDTO → Dashboard / PDF
```

## Features

- **GitHub developer analysis** from public repositories (`/users/{u}/repos?per_page=100`)
- **Technology detection** from `name`, `description`, `language`, `topics`, truncated `README` (2 KB), and dependency manifests (1 KB) — word-boundary safe, per-repo deduplicated, canonical display names
- **Evidence-strength weighting** — strong (topics/manifest/README), medium (name/description), weak (language only) for fair scoring
- **Deterministic capability scores** (`backend/frontend/database/ai/overall` 0–100) — `BASE 20 + 15*weightedSignals`, specialization-aware overall (`0.4*max + 0.3*second + 0.2*third + 0.1*fourth`)
- **Evidence-based profile classification** (`Backend/Frontend/Full Stack/AI/ML/Data/General/Unknown`), `confidence` (LOW/MEDIUM/HIGH), `specialization`, `experienceLevel` (Beginner/Intermediate/Advanced/Expert — GitHub evidence depth, not employment years), `meaningfulRepositories`, `evidenceSummary`
- **Repository evidence** — `fork/archived/size/updated_at/pushed_at` signals, `meaningful` vs tutorial/empty detection, `recency` (RECENT ≤90d / ACTIVE ≤365d / STALE)
- **Featured repositories** — up to 10 selected by quality score + stars + recency + tech relevance (not stars alone), with `technologies/recency/fork/archived` badges
- **AI qualitative insights** — Gemini `gemini-2.5-flash` (`maxOutputTokens 300`, `temperature 0.2`, `thinkingBudget 0`, retry up to 3 on 429/408/5xx) with stack-neutral fallback when unavailable
- **Unified report API** — single frontend request, backward-compatible `/final-report/{username}`
- **Bounded in-memory cache** — `ConcurrentHashMap` LRU, `TTL 30 min`, `max 500` entries (~2–5 MB), expired-first eviction, `lastAccess` touch on hit
- **Per-username single-flight** — `CompletableFuture` in `inflight` map, concurrent same-user requests share one generation; different users independent; failed generation cleans `inflight`
- **GitHub request budget** — `1` main repo-list + `max 20` deep HTTP attempts (README + contents-list + manifest file), every attempt counts even on failure
- **Responsive dark SaaS dashboard** — Overview / Repositories / Technologies / AI Insights tabs, avatar, badges, radar + progress visuals, mobile gutters tested at 320–1440px
- **PDF export** — header + candidate + scores + technologies + AI summary via `jsPDF`, paginated, no internal fields
- **Structured error handling** — `USER_NOT_FOUND` 404, `RATE_LIMITED` 429, `GITHUB_AUTH_FAILED` 503, `GITHUB_UNAVAILABLE` 503, `BAD_REQUEST` 400, `INTERNAL_ERROR` 500 (generic message, no stack leak)

## Tech Stack

**Backend**
- Java 17, Spring Boot 3.5.4, Spring Web, `RestTemplate`
- Jackson Databind, Lombok
- Maven, Spring Boot Test / JUnit 5 + Mockito
- GitHub REST API, Google Gemini API (`generativelanguage.googleapis.com`)

**Frontend**
- React 19, Vite 8, JavaScript (ES6+), CSS3 (custom design tokens, no UI framework)
- Axios, Recharts, jsPDF, `html2canvas` (transitive)

**Deployment**
- Render (backend Spring Boot jar, `PORT` env), Vercel (frontend Vite build)
- Git, GitHub

> No Redis, database/JPA, Kafka/RabbitMQ, WebSockets, Docker required for deployment, AWS/Kubernetes, microservices, Spring Cloud, Actuator, or TypeScript.

## Architecture

```
React (Vite) ──axios──▶ Spring Boot REST API
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
   GitHubService     TechnologyDetector   DeveloperScoringService
        │                   │                   │
        │              DeveloperProfileService   │
        │                   │                   │
        └──────────────▶ GeminiService ◀────────┘
                            │
                     FinalReportDTO
                            │
                 Dashboard / PDF / Cache
```

- **Cache:** `Map<String, CachedReport> reportCache` + `Map<String, CompletableFuture<FinalReportDTO>> inflight`, both `ConcurrentHashMap`. TTL 30 min, bounded 500, LRU eviction (expired first), `touch()` on hit, no background thread, bounded via `evictIfNeeded()` on write.
- **Single-flight:** `putIfAbsent(key, newFuture)` — leader generates, followers `existing.get()`; on failure `completeExceptionally` + `remove`; on success `remove` after `complete`.
- **GitHub budget:** `MAX_DEEP_REPOS=10` candidates, `MAX_DEEP_HTTP_CALLS=20` — `enrichWithDeepEvidence` counts README and manifest steps separately, skips `techSize>=2` repos to save budget.
- **Validation:** `validateReportConsistency` checks `0–100` scores, required fields, `experienceLevel` allow-list, primary language deterministic (`count desc, alphabetical tie-break`).

## API

### Canonical

```
GET /api/github/report/{username}
```

### Compatibility (same generation flow)

```
GET /api/github/final-report/{username}
```

Both normalize `username` (`trim().toLowerCase()`) and share the cache/single-flight path.

### Response — `FinalReportDTO` (public fields only)

```json
{
  "username": "octocat",
  "overallScore": 68,
  "backendScore": 65,
  "frontendScore": 50,
  "databaseScore": 35,
  "aiScore": 20,
  "technologies": { "Spring Boot": 3, "React": 2 },
  "aiAnalysis": "LEVEL: Intermediate\nTOP_STRENGTHS:\n- ...",
  "profileType": "Backend Developer",
  "confidence": "MEDIUM",
  "specialization": "BACKEND",
  "experienceLevel": "Intermediate",
  "experienceEvidence": "overall=68 meaningful=4/12 stars=18 breadth=2 depth=65 techs=5",
  "meaningfulRepositories": 4,
  "totalRepositories": 12,
  "totalStars": 18,
  "breadth": 2,
  "depth": 65,
  "distinctTechnologies": 5,
  "capabilitySignals": { "BACKEND": 4, "FRONTEND": 2, "DATABASE": 1, "AI": 0 },
  "evidenceSummary": "profile=Backend Developer backend=65(4) ... langs=[Java, TypeScript]",
  "profileAssessment": { "...structured..." },
  "languages": { "Java": 5, "TypeScript": 3 },
  "primaryLanguage": "Java",
  "featuredRepositories": [
    {
      "name": "greencart",
      "description": "Full stack grocery ...",
      "language": "Java",
      "stars": 12,
      "forks": 2,
      "updatedAt": "2024-12-01T00:00:00Z",
      "fork": false,
      "archived": false,
      "recency": "RECENT",
      "technologies": ["Spring Boot", "React"]
    }
  ]
}
```

> Internal fields (`readmeContent`, `dependencyEvidence`, `reportCache`, `inflight`, secrets) are never serialized.

### Error codes

| HTTP | `code` | Meaning |
|------|--------|---------|
| 404 | `USER_NOT_FOUND` | GitHub user not found |
| 429 | `RATE_LIMITED` | GitHub rate limit (including 403 with rate-limit body) |
| 503 | `GITHUB_AUTH_FAILED` | GitHub 403 auth/config failure |
| 503 | `GITHUB_UNAVAILABLE` | Timeout / connection failure |
| 400 | `BAD_REQUEST` | Blank username |
| 500 | `INTERNAL_ERROR` | Unexpected — generic message, no stack leak |

Legacy granular endpoints (`/analyze/{u}`, `/tech/{u}`, `/score/{u}`, `/full-analysis/{u}`, `/profile/{u}`, `/{u}`) still exist but the frontend uses only the unified report.

## Local Development

**Prerequisites:** Java 17+, Maven 3.9+, Node 18+, GitHub PAT, Gemini API key.

**Backend**
```bash
cd backend
# create src/main/resources/application.properties from the example
cp src/main/resources/application-example.properties src/main/resources/application.properties
# then set GITHUB_TOKEN and GEMINI_API_KEY (see Environment Variables)

./mvnw test          # 159 tests
./mvnw spring-boot:run
# or
./mvnw package -DskipTests && java -jar target/*.jar
```

Backend listens on `${PORT:8080}` → `http://localhost:8080/api/github/report/{username}`.

**Frontend**
```bash
cd frontend
npm install
# set VITE_API_BASE_URL to your backend
echo "VITE_API_BASE_URL=http://localhost:8080/api/github" > .env.local
npm run dev      # http://localhost:5173
npm run build    # production build → dist/
```

## Environment Variables

Never commit real values. Use placeholders:

**Backend — `backend/src/main/resources/application.properties` (gitignored) or env**

```
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
GEMINI_API_KEY=AIxxxxxxxxxxxxxxxxxxxx
PORT=8080
```

`application-example.properties` is the safe template committed to Git:

```properties
spring.application.name=devscout-ai
server.port=${PORT:8080}
devscout.cache.ttl.minutes=30
devscout.cache.max-entries=500
gemini.api.key=${GEMINI_API_KEY}
gemini.model=gemini-2.5-flash
gemini.timeout.connect.ms=10000
gemini.timeout.read.ms=60000
gemini.retry.max-attempts=3
github.token=${GITHUB_TOKEN}
```

**Frontend — `frontend/.env.local` (gitignored)**

```
VITE_API_BASE_URL=http://localhost:8080/api/github
# production example:
# VITE_API_BASE_URL=https://devscout-ai-backend.onrender.com/api/github
```

Frontend falls back to the Render URL when `VITE_API_BASE_URL` is unset (`frontend/src/services/api.js:3`).

## Deployment

**Backend — Render (Spring Boot jar, no Docker required)**
- Build: `mvn package` (or Render Maven build)
- Start: `java -jar target/*.jar` — respects `server.port=${PORT:8080}`
- Environment: set `GITHUB_TOKEN` and `GEMINI_API_KEY` in Render dashboard
- Health: `GET /api/github/report/{knownUser}` should return `200`

**Frontend — Vercel (Vite)**
- Build: `npm run build` → `dist/` (Vercel auto-detects Vite)
- Environment: set `VITE_API_BASE_URL` to the deployed Render backend URL (no trailing slash needed — code strips it)
- CORS: backend allows `http://localhost:5173` and `https://dev-scout-ai.vercel.app` (`CorsConfig.java:20`); add additional origins there if you deploy to a new domain

No Docker, Redis, or database is required.

## Limitations

- **In-memory cache is instance-local** — each Render instance has its own `ConcurrentHashMap`; cache is **lost on restart/redeploy** and not shared across scaled instances.
- **GitHub API limits evidence** — only `per_page=100` repos and at most `20` deep enrichment requests per report; private repos, commits, and PRs are not analyzed.
- **Gemini is qualitative** — AI insights are interpretive text (level/strengths/improvements/hiring line), not verified facts; a deterministic stack-neutral fallback is used when Gemini is unavailable.
- **GitHub evidence ≠ employment history** — `experienceLevel`/`profileType` describe repository evidence depth, not years of professional experience.
- **Bundle size** — Vite production JS is ~`945 kB` (`gzip ~298 kB`) dominated by `Recharts` + `jsPDF`/`html2canvas`; acceptable for current scope, lazy-loading PDF/chart code is a future optimization.

## Screenshots

All paths verified in `screenshots/`:

| View | File |
|------|------|
| Home | `screenshots/home-page.png` |
| Developer dashboard | `screenshots/candidate-dashboard.png` |
| Skill intelligence | `screenshots/candidate-skill.png` |
| AI insights | `screenshots/ai-insight.png` |
| PDF export | `screenshots/pdf-export.png` |

## Project Structure

```
devscout-ai/
├── backend/                          # Spring Boot (Java 17, Maven)
│   ├── pom.xml
│   ├── src/main/java/com/soubhagya/devscout/
│   │   ├── DevscoutAiApplication.java
│   │   ├── config/CorsConfig.java
│   │   ├── controller/GitHubController.java
│   │   ├── service/GitHubService.java          # cache + single-flight + budget + orchestration
│   │   ├── service/GeminiService.java          # 2.5-flash, 300 tokens, 0.2, thinking 0, retry
│   │   ├── service/TechnologyDetector.java     # evidence-strength detection
│   │   ├── service/DeveloperScoringService.java
│   │   ├── service/DeveloperProfileService.java
│   │   ├── dto/FinalReportDTO.java, GitHubRepoDTO.java, FeaturedRepositoryDTO.java, ...
│   │   └── exception/GlobalExceptionHandler.java
│   └── src/main/resources/application-example.properties
├── frontend/                         # React 19 + Vite 8
│   ├── package.json
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── App.jsx                   # single unified request, requestId dedup
│       ├── components/               # Navbar, Hero, SearchBar, Dashboard, ScoreCard, ...
│       ├── components/sections/      # Overview / Repositories / Technologies / Insights
│       ├── services/api.js           # VITE_API_BASE_URL
│       ├── services/pdfService.js    # jsPDF export
│       └── styles/                   # variables, base, hero, analysis, report, responsive, ...
├── screenshots/                      # 5 verified images
├── .gitignore                        # ignores application.properties, .env, target/, node_modules/
└── README.md
```

## Testing

- **Backend:** `159` tests passing (`mvn test`) — `ReportIntegrationTest`, `TechnologyDetectorTest`, `DeveloperScoringServiceTest`, `DeveloperProfileServiceTest`, `FairnessRegressionTest`, `Phase3RepositoryEvidenceTest`, `Phase4HardeningTest`, `Phase4UnifiedReportTest`, `Phase5CacheTest`, `Phase6AnalysisQualityTest`
- **Frontend:** `npm run build` passing (Vite production build, `dist/` generated)

No additional E2E/integration tests are claimed.

## Author

**Soubhagya Kumar Behera** — Java Full Stack (Spring Boot · React · AI integration)
- GitHub: https://github.com/soubhagya-behera
- LinkedIn: https://www.linkedin.com/in/soubhagyakumar-java
- Portfolio: https://soubhagya-dev.vercel.app
