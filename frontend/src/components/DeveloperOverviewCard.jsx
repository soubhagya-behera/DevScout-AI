import GitHubAvatar from "./GitHubAvatar";

function DeveloperOverviewCard({
  profile,
  report,
  username,
  overallScore,
  onDownload,
  onNewAnalysis,
}) {
  const level =
    overallScore >= 80
      ? "Expert"
      : overallScore >= 60
        ? "Advanced"
        : overallScore >= 40
          ? "Intermediate"
          : "Beginner";

  const displayName = username || report?.username || "Unknown";
  const profileType = report?.profileType || "Unknown";
  const confidence = report?.confidence;
  const experience = report?.experienceLevel || level;
  const primaryLanguage =
    report?.primaryLanguage || profile?.primaryLanguage || "Unknown";
  const totalStars = report?.totalStars ?? 0;
  const meaningful = report?.meaningfulRepositories ?? profile?.totalRepositories ?? 0;

  return (
    <section className="panel overview-panel">
      <div className="overview-main">
        <div className="overview-identity">
          <GitHubAvatar
            username={username || report?.username}
            avatarUrl={report?.avatarUrl || profile?.avatarUrl}
          />
          <div className="overview-identity-text">
            <h3 className="overview-name">{displayName}</h3>
            <a
              className="dashboard-sub"
              href={`https://github.com/${encodeURIComponent(username || report?.username || "")}`}
              target="_blank"
              rel="noreferrer"
            >
              github.com/{username || report?.username}
            </a>
            <div className="dashboard-badges">
              <span className="profile-badge">{profileType}</span>
              {confidence && (
                <span className={`confidence-badge confidence-${confidence.toLowerCase()}`}>
                  Confidence: {confidence}
                </span>
              )}
              {experience && (
                <span className="experience-badge" title="Represents GitHub evidence depth, not employment years">
                  {experience} · GitHub evidence
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="overview-side">
          <div className="overview-actions">
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
        </div>
      </div>

      <div className="overview-stats">
        <div className="overview-stat">
          <span className="overview-stat-value">{overallScore}</span>
          <span className="overview-stat-label">GitHub evidence score</span>
          <span className="overview-stat-sub">{level}</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">
            {profile?.totalRepositories ?? 0}
          </span>
          <span className="overview-stat-label">Repositories</span>
          <span className="overview-stat-sub">{meaningful} meaningful</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">
            {primaryLanguage}
          </span>
          <span className="overview-stat-label">Primary language</span>
          <span className="overview-stat-sub">by repo count</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">{totalStars}</span>
          <span className="overview-stat-label">Stars earned</span>
          <span className="overview-stat-sub">across repos</span>
        </div>
      </div>
    </section>
  );
}

export default DeveloperOverviewCard;
