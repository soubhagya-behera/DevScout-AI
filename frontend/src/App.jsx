import { useState } from "react";
import SearchBar from "./components/SearchBar";
import "./styles/App.css";
import ScoreCard from "./components/ScoreCard";
import TechnologyBadge from "./components/TechnologyBadge";
import LoadingSpinner from "./components/LoadingSpinner";
import SkillProgress from "./components/SkillProgress";
import CandidateSummary from "./components/CandidateSummary";
import { downloadReport } from "./services/pdfService";
import { getReport, getProfile } from "./services/api";
import DeveloperOverviewCard from "./components/DeveloperOverviewCard";
import RadarSkillChart from "./components/RadarSkillChart";
import AIInsights from "./components/AIInsights";

function App() {
  const [username, setUsername] = useState("");
  const [report, setReport] = useState(null);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleAnalyze = async () => {
    if (!username.trim()) {
  setError("Please enter a GitHub username");
  return;
}

setError("");

    try {
      setLoading(true);
      const profileData =
  await getProfile(
    username
  );

setProfile(
  profileData
);


const data =
  await getReport(
    username
  );
      window.scrollTo({
        top: 500,
        behavior: "smooth",
      });
      setReport(data);
    } catch (error) {

  console.error(error);

  setReport(null);

  setError(
    "GitHub user not found. Please enter a valid username."
  );
} finally {
      setLoading(false);
    }
  };

  return (
    <div className="app">
      <div className="hero">

  <div className="hero-badge">
    🚀 AI Powered Developer Intelligence
  </div>

  <h1 className="title">
    GitHire AI
  </h1>

  <h2 className="hero-heading">
    AI-Powered Hiring Intelligence
  </h2>

  <p className="subtitle">
    Skill Assessment • Experience Detection • Hiring Recommendation
  </p>

</div>

      <SearchBar
        username={username}
        setUsername={setUsername}
        handleAnalyze={handleAnalyze}
      />

      {error && (
  <div className="error-message">
    ❌ {error}
  </div>
)}

      {loading && <LoadingSpinner />}

      {report && (
        <>
        <h2 className="section-title">
  Developer Overview
</h2>


<DeveloperOverviewCard
  profile={profile}
  username={username}
  overallScore={report.overallScore}
/>
          <div className="section-divider"></div>

<RadarSkillChart
  backend={report.backendScore}
  frontend={report.frontendScore}
  database={report.databaseScore}
  ai={report.aiScore}
/>

          <h2 className="section-title">
  Technical Assessment
</h2>

          <div className="scores-grid">
            <ScoreCard title="Backend" score={report.backendScore} />
            <ScoreCard title="Frontend" score={report.frontendScore} />
            <ScoreCard title="Database" score={report.databaseScore} />
            <ScoreCard title="AI" score={report.aiScore} />
          </div>

          <div className="skills-section">

            <h2>Skill Breakdown</h2>
            <SkillProgress title="Backend" score={report.backendScore} />
            <SkillProgress title="Frontend" score={report.frontendScore} />
            <SkillProgress title="Database" score={report.databaseScore} />
            <SkillProgress title="AI" score={report.aiScore} />
          </div>

          <div className="section-divider"></div>

          <h2 className="section-title">
  Technology Stack
</h2>

      <div className="technologies-section">
  <div className="tech-grid">
    {Object.entries(report.technologies).map(
      ([tech, count]) => (
        <TechnologyBadge
          key={tech}
          tech={tech}
          count={count}
        />
      )
    )}
  </div>
</div>    

<div className="section-divider"></div>

          <h2 className="section-title">
  AI Recruiter Summary
</h2>

          
<AIInsights analysis={report.aiAnalysis} />

          <div className="report-section">
  <h3>📄 Export Developer Report</h3>

  <p>
  Export a recruiter-ready PDF report with AI insights and developer scoring.
</p>

  <button
    className="download-btn"
    onClick={() => downloadReport(username, report)}
  >
    Download Report
  </button>
</div>
        </>
        
      )}
    </div>
  );
}

export default App;