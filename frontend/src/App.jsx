import { useRef, useState } from "react";
import Navbar from "./components/Navbar";
import Hero from "./components/Hero";
import ProductPreview from "./components/ProductPreview";
import LoadingSequence from "./components/LoadingSequence";
import Dashboard from "./components/Dashboard";
import { downloadReport } from "./services/pdfService";
import {
  getReport,
  getProfile,
  getRepoAnalysis,
  getAnalyze
} from "./services/api";
import "./styles/App.css";

const MIN_STEP_MS = 750;

function App() {
  const [username, setUsername] = useState("");
  const [report, setReport] = useState(null);
  const [profile, setProfile] = useState(null);
  const [languages, setLanguages] = useState(null);
  const [repoAnalysis, setRepoAnalysis] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [phase, setPhase] = useState(0);
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
    setPhase(0);
    setReport(null);
    setProfile(null);
    setLanguages(null);
    setRepoAnalysis(null);

    const t0 = Date.now();
    let settled = false;

    const isCurrent = () => requestIdRef.current === requestId;

    const finishLoading = () => {
      if (settled || !isCurrent()) return;
      settled = true;
      setLoading(false);
    };

    const schedulePhase = (n) => {
      const wait = Math.max(0, n * MIN_STEP_MS - (Date.now() - t0));
      window.setTimeout(() => {
        if (isCurrent()) {
          setPhase((current) => Math.max(current, n));
        }
      }, wait);
    };

    getProfile(username)
      .then((profileData) => {
        if (!isCurrent()) return;
        setProfile(profileData);
        schedulePhase(1);
      })
      .catch(() => {});

    getReport(username)
      .then((reportData) => {
        if (!isCurrent()) return;
        setReport(reportData);
        schedulePhase(3);
        window.setTimeout(() => {
          finishLoading();
          scrollToDashboard();
        }, 450);
      })
      .catch((err) => {
        if (!isCurrent()) return;
        console.error(err);
        setError("GitHub user not found. Please enter a valid username.");
        finishLoading();
      });

    getRepoAnalysis(username)
      .then((data) => {
        if (!isCurrent()) return;
        if (data) setRepoAnalysis(data);
        schedulePhase(2);
      })
      .catch(() => schedulePhase(2));

    getAnalyze(username)
      .then((data) => {
        if (!isCurrent()) return;
        if (data && data.languages) setLanguages(data.languages);
      })
      .catch(() => {});
  };

  const handleNewAnalysis = () => {
    requestIdRef.current += 1;
    setReport(null);
    setProfile(null);
    setLanguages(null);
    setRepoAnalysis(null);
    setError("");
    setPhase(0);
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
          <LoadingSequence phase={phase} username={username} />
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