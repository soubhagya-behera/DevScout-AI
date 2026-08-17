import DeveloperOverviewCard from "../DeveloperOverviewCard";
import ScoreCard from "../ScoreCard";
import RadarSkillChart from "../RadarSkillChart";
import SkillProgress from "../SkillProgress";

function OverviewSection({ profile, report, username, languages }) {
  const languageEntries = Object.entries(languages || {})
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6);

  const maxLanguageCount = Math.max(
    1,
    ...languageEntries.map(([, count]) => count)
  );

  return (
    <div className="section-stack">
      <DeveloperOverviewCard
        profile={profile}
        username={username}
        overallScore={report.overallScore}
      />

      <section className="panel skills-panel">
        <div className="panel-header">
          <h3 className="panel-title">Engineering skill scores</h3>
          <span className="panel-note">Detected from technology usage</span>
        </div>
        <div className="skills-layout">
          <div className="skills-grid">
            <ScoreCard
              title="Backend"
              score={report.backendScore}
              delay={0}
            />
            <ScoreCard
              title="Frontend"
              score={report.frontendScore}
              delay={80}
            />
            <ScoreCard
              title="Database"
              score={report.databaseScore}
              delay={160}
            />
            <ScoreCard
              title="AI"
              score={report.aiScore}
              delay={240}
            />
          </div>
          <div className="skills-radar">
            <RadarSkillChart
              backend={report.backendScore}
              frontend={report.frontendScore}
              database={report.databaseScore}
              ai={report.aiScore}
            />
          </div>
        </div>
      </section>

      {languageEntries.length > 0 && (
        <section className="panel languages-panel">
          <div className="panel-header">
            <h3 className="panel-title">Languages</h3>
            <span className="panel-note">Across public repositories</span>
          </div>
          <div className="languages-list">
            {languageEntries.map(([language, count], index) => (
              <SkillProgress
                key={language}
                title={language}
                score={Math.round((count / maxLanguageCount) * 100)}
                detail={`${count} repo${count === 1 ? "" : "s"}`}
                delay={index * 60}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

export default OverviewSection;