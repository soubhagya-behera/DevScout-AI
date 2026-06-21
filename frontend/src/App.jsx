import { useState } from "react";
import SearchBar from "./components/SearchBar";
import { getReport } from "./services/api";
import "./styles/App.css";
import ScoreCard from "./components/ScoreCard";
import TechnologyCard from "./components/TechnologyCard";
import TechnologyBadge from "./components/TechnologyBadge";

function App() {

  const [username, setUsername] =
    useState("");

  const [report, setReport] =
    useState(null);

  const [loading, setLoading] =
    useState(false);

  const handleAnalyze =
    async () => {

      if (!username.trim())
        return;

      try {

        setLoading(true);

        const data =
          await getReport(username);

        setReport(data);

      } catch (error) {

        console.error(error);

        alert(
          "Error fetching report"
        );

      } finally {

        setLoading(false);
      }
    };

  return (
    <div className="app">

      <h1 className="title">
        DevScout AI
      </h1>

      <p className="subtitle">
        AI-Powered GitHub Profile Analyzer
      </p>

      <SearchBar
        username={username}
        setUsername={setUsername}
        handleAnalyze={handleAnalyze}
      />

      {loading && (
        <h2>Analyzing...</h2>
      )}

      {report && (

  <>

    <div className="overall-section">

      <ScoreCard
        title="Overall Score"
        score={report.overallScore}
      />

    </div>

    <div className="scores-grid">

      <ScoreCard
        title="Backend"
        score={report.backendScore}
      />

      <ScoreCard
        title="Frontend"
        score={report.frontendScore}
      />

      <ScoreCard
        title="Database"
        score={report.databaseScore}
      />

      <ScoreCard
        title="AI"
        score={report.aiScore}
      />

    </div>

    {report && (

  <div className="technologies-section">

    <h2>
      Technologies Detected
    </h2>

    <div className="tech-grid">

      {Object.entries(
        report.technologies
      ).map(([tech, count]) => (

        <TechnologyBadge
          key={tech}
          tech={tech}
          count={count}
        />

      ))}

    </div>

  </div>



)}

  </>
  

)}

    </div>
  );
}

export default App;