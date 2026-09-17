import { useRef, useState } from "react";
import Navbar from "./components/Navbar";
import Hero from "./components/Hero";
import ProductPreview from "./components/ProductPreview";
import LoadingSequence from "./components/LoadingSequence";
import Dashboard from "./components/Dashboard";
import { downloadReport } from "./services/pdfService";
import {
  getUnifiedReport
} from "./services/api";
import "./styles/App.css";

function App() {
  const [username, setUsername] = useState("");
  const [report, setReport] = useState(null);
  const [profile, setProfile] = useState(null);
  const [languages, setLanguages] = useState(null);
  const [repoAnalysis, setRepoAnalysis] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const requestIdRef = useRef(0);

  const scrollToDashboard = () => {
    window.setTimeout(() => {
      const target = document.getElementById("dashboard");
      if (target) {
        target.scrollIntoView({ behavior: "smooth", block: "start" });
      } else {
        window.scrollTo({ top: 500, behavior: "smooth" });
      }
    }, 0);
  };

  const handleAnalyze = () => {
    if (!username.trim()) {
      setError("Please enter a GitHub username");
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;

    setError("");
    setLoading(true);
    setReport(null);
    setProfile(null);
    setLanguages(null);
    setRepoAnalysis(null);

    let settled = false;

    const isCurrent = () => requestIdRef.current === requestId;

    const finishLoading = () => {
      if (settled || !isCurrent()) return;
      settled = true;
      setLoading(false);
    };

    const trimmed = username.trim();

    getUnifiedReport(trimmed)
      .then((reportData) => {
        if (!isCurrent()) return;
        setReport(reportData);
        const derivedProfile = {
          username: reportData.username,
          totalRepositories: reportData.totalRepositories,
          primaryLanguage: reportData.primaryLanguage || "Unknown",
        };
        setProfile(derivedProfile);
        setLanguages(reportData.languages || {});
        if (reportData.featuredRepositories) {
          setRepoAnalysis(reportData.featuredRepositories);
        } else {
          setRepoAnalysis([]);
        }
        window.setTimeout(() => {
          finishLoading();
          scrollToDashboard();
        }, 320);
      })
      .catch((err) => {
        if (!isCurrent()) return;
        console.error(err);
        const status = err?.response?.status;
        const code = err?.response?.data?.code;
        if (status === 404 || code === "USER_NOT_FOUND") {
          setError("GitHub user not found. Please enter a valid username.");
        } else if (status === 429 || code === "RATE_LIMITED") {
          setError("GitHub rate limit exceeded. Please try again in a few minutes.");
        } else if (code === "GITHUB_UNAVAILABLE") {
          setError("GitHub is temporarily unavailable. Please try again shortly.");
        } else if (status === 503 || code === "GITHUB_AUTH_FAILED") {
          setError("GitHub service is temporarily unavailable. Please try again later.");
        } else if (status === 400) {
          setError("Please enter a valid GitHub username.");
        } else {
          setError("Failed to analyze profile. Please try again.");
        }
        finishLoading();
      });
  };

  const handleNewAnalysis = () => {
    requestIdRef.current += 1;
    setReport(null);
    setProfile(null);
    setLanguages(null);
    setRepoAnalysis(null);
    setError("");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const hasDashboard = !loading && !!report && !!profile;

  return (
    <div className={`app ${hasDashboard ? "has-dashboard" : ""}`}>
      <Navbar />

      <main className="app-main">
        <Hero
          username={username}
          setUsername={setUsername}
          handleAnalyze={handleAnalyze}
          error={error}
          loading={loading}
        />

        {loading && (
          <LoadingSequence username={username} />
        )}

        {!loading && !report && <ProductPreview />}

        {!loading && report && profile && (
          <Dashboard
            profile={profile}
            report={report}
            username={username}
            languages={languages}
            repoAnalysis={repoAnalysis}
            onDownload={() => downloadReport(username, report)}
            onNewAnalysis={handleNewAnalysis}
          />
        )}
      </main>

      <footer className="app-footer">
        <span className="app-footer-brand">DevScout</span>
        <span className="app-footer-divider" aria-hidden="true"></span>
        <span>Developer intelligence from GitHub activity.</span>
        <span className="app-footer-divider" aria-hidden="true"></span>
        <span className="app-footer-credit">Built by Soubhagya Kumar Behera</span>
      </footer>
    </div>
  );
}

export default App;