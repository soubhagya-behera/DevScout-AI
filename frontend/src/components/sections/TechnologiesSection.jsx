import TechnologyBadge from "../TechnologyBadge";

function TechnologiesSection({ technologies }) {
  const entries = Object.entries(technologies || {})
    .sort((a, b) => b[1] - a[1]);

  const maxCount = Math.max(1, ...entries.map(([, count]) => count));

  return (
    <div className="section-stack">
      <section className="panel techs-panel">
        <div className="panel-header">
          <h3 className="panel-title">Technology stack</h3>
          <span className="panel-note">
            Detected from repository descriptions
          </span>
        </div>

        {entries.length === 0 ? (
          <div className="empty-state">
            <p>No technologies detected in this profile.</p>
          </div>
        ) : (
          <div className="tech-list">
            {entries.map(([tech, count], index) => (
              <div
                className="tech-row"
                key={tech}
                style={{ animationDelay: `${index * 40}ms` }}
              >
                <div className="tech-row-head">
                  <TechnologyBadge tech={tech} count={count} />
                  <span className="tech-row-count">
                    {count} repo{count === 1 ? "" : "s"}
                  </span>
                </div>
                <div className="progress-track">
                  <div
                    className="progress-fill"
                    style={{
                      width: `${Math.round((count / maxCount) * 100)}%`,
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

export default TechnologiesSection;