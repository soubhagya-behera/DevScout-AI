function RepositoriesSection({ repoAnalysis, totalRepositories }) {
  const repos = repoAnalysis || [];

  const extractTechLegacy = (analysis) => {
    const match = analysis?.match(/Technologies detected:\s*(.*)/i);
    if (!match) return [];
    const raw = match[1].trim();
    if (raw === "None" || raw === "") return [];
    return raw.split(",").map((item) => item.trim()).filter(Boolean);
  };

  const getTechs = (repo) => {
    if (Array.isArray(repo.technologies)) return repo.technologies;
    if (repo.analysis) return extractTechLegacy(repo.analysis);
    return [];
  };

  const getName = (repo) => repo.name || repo.repositoryName || "Unknown";

  return (
    <div className="section-stack">
      <section className="panel repos-panel">
        <div className="panel-header">
          <h3 className="panel-title">Repository analysis</h3>
          {totalRepositories != null && (
            <span className="panel-note">
              {repos.length} of {totalRepositories} repositories analyzed
            </span>
          )}
        </div>

        {repos.length === 0 ? (
          <div className="empty-state">
            <p>Repository analysis is not available for this profile.</p>
            <p className="empty-state-sub">
              The GitHub report still includes scores, technologies and AI
              insights.
            </p>
          </div>
        ) : (
          <div className="repo-grid">
            {repos.map((repo) => {
              const techs = getTechs(repo);
              const name = getName(repo);
              const recency = repo.recency;
              const updated = repo.updatedAt ? new Date(repo.updatedAt).toLocaleDateString() : null;
              return (
                <article key={name} className="repo-card">
                  <div className="repo-card-header">
                    <h4 className="repo-card-name">{name}</h4>
                    <div className="repo-card-badges">
                      {repo.language && <span className="repo-badge repo-badge-lang">{repo.language}</span>}
                      {repo.stars != null && <span className="repo-badge" title="Stars">★ {repo.stars}</span>}
                      {repo.forks != null && repo.forks > 0 && <span className="repo-badge" title="Forks">⑂ {repo.forks}</span>}
                      {recency && <span className={`repo-badge recency-${recency.toLowerCase()}`}>{recency}</span>}
                      {repo.fork && <span className="repo-badge repo-badge-fork">fork</span>}
                      {repo.archived && <span className="repo-badge repo-badge-archived">archived</span>}
                    </div>
                  </div>
                  {repo.description && <p className="repo-card-desc">{repo.description}</p>}
                  {updated && <div className="repo-card-meta">Updated {updated}</div>}
                  <div className="repo-card-techs">
                    {techs.length > 0 ? (
                      techs.map((tech) => (
                        <span className="tech-badge" key={tech}>{tech}</span>
                      ))
                    ) : (
                      <span className="repo-none">No technologies detected</span>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}

export default RepositoriesSection;
