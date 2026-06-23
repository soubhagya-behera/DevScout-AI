import { useState } from "react";
import SearchBar from "./components/SearchBar";
import "./styles/App.css";
import ScoreCard from "./components/ScoreCard";
import TechnologyBadge from "./components/TechnologyBadge";
import LoadingSpinner from "./components/LoadingSpinner";
import ProfileCard from "./components/ProfileCard";
import SkillProgress from "./components/SkillProgress";
import CandidateSummary from "./components/CandidateSummary";
import { downloadReport } from "./services/pdfService";
import ProfileInfoCard from "./components/ProfileInfoCard";
import HiringCard from "./components/HiringCard";
import { getReport, getProfile } from "./services/api";


function App() {
  const [username, setUsername] = useState("");
  const [report, setReport] = useState(null);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleAnalyze = async () => {
    if (!username.trim()) return;

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
      alert("Error fetching report");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app">
      <div className="hero">
        <h1 className="title">DevScout AI</h1>
        <h2 className="hero-heading">
          Analyze Any GitHub Developer
          <br />
          with AI-Powered Insights
        </h2>
        <p className="subtitle">
          Technology Detection • Developer Scoring • AI Recommendations
        </p>
      </div>

      <SearchBar
        username={username}
        setUsername={setUsername}
        handleAnalyze={handleAnalyze}
      />

      {loading && <LoadingSpinner />}

      {report && (
        <>
        <h2 className="section-title">
  Developer Overview
</h2>
        <ProfileInfoCard
      profile={profile}
    />

        
          <ProfileCard username={username} overallScore={report.overallScore} />
  
          <div className="overall-section">
            <ScoreCard title="Overall Score" score={report.overallScore} />
          </div>

          <div className="section-divider"></div>

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

          <CandidateSummary analysis={report.aiAnalysis} />

<div className="section-divider"></div>
          <HiringCard />

          <button
            className="download-btn"
            onClick={() => downloadReport(username, report)}
          >
            Download Report
          </button>
        </>
        
      )}
    </div>
  );
}

export default App;