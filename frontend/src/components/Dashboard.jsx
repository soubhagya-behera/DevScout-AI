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
        <div className="dashboard-body">
          <Sidebar tabs={tabs} active={active} onSelect={setActive} />

          <main className="dashboard-content">
            {active === "overview" && (
              <OverviewSection
                profile={profile}
                report={report}
                username={username}
                languages={languages}
                onDownload={onDownload}
                onNewAnalysis={onNewAnalysis}
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

export default Dashboard;