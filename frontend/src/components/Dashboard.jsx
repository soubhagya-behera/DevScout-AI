import { useState } from "react";
import Sidebar from "./Sidebar";
import OverviewSection from "./sections/OverviewSection";
import RepositoriesSection from "./sections/RepositoriesSection";
import TechnologiesSection from "./sections/TechnologiesSection";
import InsightsSection from "./sections/InsightsSection";

function Dashboard({
  profile,
  report,
  username,
  languages,
  repoAnalysis,
  onDownload,
  onNewAnalysis
}) {
  const [active, setActive] = useState("overview");

  const tabs = [
    { id: "overview", label: "Overview" },
    {
      id: "repositories",
      label: "Repositories",
      count: profile?.totalRepositories ?? 0,
    },
    {
      id: "technologies",
      label: "Technologies",
      count: report?.technologies
        ? Object.keys(report.technologies).length
        : 0,
    },
    { id: "insights", label: "AI Insights" },
  ];

  return (
    <section className="dashboard" id="dashboard">
      <div className="dashboard-inner">
        <header className="dashboard-header">
          <div className="dashboard-identity">
            <div className="avatar" aria-hidden="true">{(username || report.username || "?").charAt(0).toUpperCase()}</div>
            <div className="dashboard-identity-text">
              <div className="dashboard-username">{report.username || username || "Unknown"}</div>
              <a className="dashboard-sub" href={`https://github.com/${encodeURIComponent(username || report.username || "")}`} target="_blank" rel="noreferrer">github.com/{username || report.username}</a>
              <div className="dashboard-badges">
                <span className="profile-badge">{report.profileType || "Unknown"}</span>
                {report.confidence && (
                  <span className={`confidence-badge confidence-${report.confidence.toLowerCase()}`}>
                    Confidence: {report.confidence}
                  </span>
                )}
                {report.experienceLevel && (
                  <span className="experience-badge" title="Represents GitHub evidence depth, not employment years">
                    {report.experienceLevel} · GitHub evidence
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="dashboard-actions">
            <div className="overall-group">
              <span className="overall-chip" aria-label={`Overall GitHub evidence score ${report.overallScore} out of 100`}>
                <span className="overall-chip-label">GitHub evidence score</span>
                <strong>{report.overallScore}</strong>
                <span className="overall-chip-level">{overallLevel(report.overallScore)}</span>
              </span>
              {report.primaryLanguage && report.primaryLanguage !== "Unknown" && (
                <span className="primary-lang-badge">{report.primaryLanguage}</span>
              )}
            </div>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onDownload}
              aria-label="Export report as PDF"
            >
              Export PDF
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={onNewAnalysis}
              aria-label="Start new analysis"
            >
              New analysis
            </button>
          </div>
        </header>

        <div className="dashboard-body">
          <Sidebar tabs={tabs} active={active} onSelect={setActive} />

          <main className="dashboard-content">
            {active === "overview" && (
              <OverviewSection
                profile={profile}
                report={report}
                username={username}
                languages={languages}
              />
            )}
            {active === "repositories" && (
              <RepositoriesSection
                repoAnalysis={repoAnalysis}
                totalRepositories={profile?.totalRepositories}
              />
            )}
            {active === "technologies" && (
              <TechnologiesSection technologies={report.technologies} />
            )}
            {active === "insights" && (
              <InsightsSection analysis={report.aiAnalysis} />
            )}
          </main>
        </div>
      </div>
    </section>
  );
}

function overallLevel(score) {
  if (score >= 80) return "Expert";
  if (score >= 60) return "Advanced";
  if (score >= 40) return "Intermediate";
  return "Beginner";
}

export default Dashboard;