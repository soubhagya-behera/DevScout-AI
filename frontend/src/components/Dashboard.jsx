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
            <div className="avatar">{username.charAt(0).toUpperCase()}</div>
            <div className="dashboard-identity-text">
              <div className="dashboard-username">{username}</div>
              <div className="dashboard-sub">github.com/{username}</div>
            </div>
          </div>

          <div className="dashboard-actions">
            <span className="level-tag">
              {overallLevel(report.overallScore)}
            </span>
            <span className="overall-chip">
              Score <strong>{report.overallScore}</strong>
            </span>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onDownload}
            >
              Export PDF
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={onNewAnalysis}
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