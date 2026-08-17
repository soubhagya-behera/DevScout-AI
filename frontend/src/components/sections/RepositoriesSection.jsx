function RepositoriesSection({ repoAnalysis, totalRepositories }) {
  const repos = repoAnalysis || [];

  const extractTech = (analysis) => {
    const match = analysis?.match(/Technologies detected:\s*(.*)/i);
    if (!match) return [];
    const raw = match[1].trim();
    if (raw === "None" || raw === "") return [];
    return raw.split(",").map((item) => item.trim()).filter(Boolean);
  };

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
          <div className="repo-table-wrap">
            <table className="repo-table">
              <thead>
                <tr>
                  <th>Repository</th>
                  <th>Technology signals</th>
                </tr>
              </thead>
              <tbody>
                {repos.map((repo) => {
                  const techs = extractTech(repo.analysis);
                  return (
                    <tr key={repo.repositoryName}>
                      <td className="repo-name">{repo.repositoryName}</td>
                      <td>
                        {techs.length > 0 ? (
                          <span className="repo-techs">
                            {techs.map((tech) => (
                              <span className="tech-badge" key={tech}>
                                {tech}
                              </span>
                            ))}
                          </span>
                        ) : (
                          <span className="repo-none">
                            No technologies detected
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

export default RepositoriesSection;