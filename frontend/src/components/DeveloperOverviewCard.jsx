import { useCountUp } from "../hooks/useCountUp";

function ScoreRing({ value, size = 96, strokeWidth = 6 }) {
  const animated = useCountUp(value, { duration: 1200, delay: 300 });
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - animated / 100);

  return (
    <div className="score-ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} aria-hidden="true">
        <circle
          className="score-ring-track"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={strokeWidth}
        />
        <circle
          className="score-ring-progress"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
        />
      </svg>
      <div className="score-ring-value">{animated}</div>
    </div>
  );
}

function DeveloperOverviewCard({ profile, report, username, overallScore }) {
  const level =
    overallScore >= 80
      ? "Expert"
      : overallScore >= 60
        ? "Advanced"
        : overallScore >= 40
          ? "Intermediate"
          : "Beginner";

  const profileType = report?.profileType || level;
  const confidence = report?.confidence;
  const experience = report?.experienceLevel || level;
  const specialization = report?.specialization;
  const totalStars = report?.totalStars ?? 0;
  const meaningful = report?.meaningfulRepositories ?? profile?.totalRepositories ?? 0;

  return (
    <section className="panel overview-panel">
      <div className="overview-main">
        <div className="overview-identity">
          <div className="avatar avatar-xl" aria-hidden="true">
            {(username || report?.username || "?").charAt(0).toUpperCase()}
          </div>
          <div className="overview-identity-text">
            <h3 className="overview-name">{username || report?.username || "Unknown"}</h3>
            <div className="overview-meta">
              <span>{profileType}</span>
              {specialization && specialization !== "GENERAL" && (
                <span className="overview-specialization"> · {specialization}</span>
              )}
              {confidence && (
                <span className={`overview-confidence confidence-${confidence.toLowerCase()}`}> · {confidence} confidence</span>
              )}
            </div>
            <div className="overview-tags">
              <span className="level-tag" title="Overall GitHub evidence score level">{level}</span>
              <span className="evidence-tag" title={report?.experienceEvidence || "GitHub evidence depth"}>
                {experience} · GitHub evidence
              </span>
            </div>
            {report?.evidenceSummary && (
              <p className="overview-evidence-summary">{report.evidenceSummary}</p>
            )}
          </div>
        </div>

        <div className="overview-score">
          <ScoreRing value={overallScore} />
          <span className="overview-score-label">GitHub evidence score</span>
          <span className="overview-score-hint">0–100 · deterministic</span>
        </div>
      </div>

      <div className="overview-stats">
        <div className="overview-stat">
          <span className="overview-stat-value">
            {profile?.totalRepositories ?? 0}
          </span>
          <span className="overview-stat-label">Repositories</span>
          <span className="overview-stat-sub">{meaningful} meaningful</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">
            {profile?.primaryLanguage ?? "Unknown"}
          </span>
          <span className="overview-stat-label">Primary language</span>
          <span className="overview-stat-sub">by repo count</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">{totalStars}</span>
          <span className="overview-stat-label">Stars earned</span>
          <span className="overview-stat-sub">across repos</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">{experience}</span>
          <span className="overview-stat-label">Evidence depth</span>
          <span className="overview-stat-sub" title="Based on GitHub evidence, not employment">not employment years</span>
        </div>
      </div>
    </section>
  );
}

export default DeveloperOverviewCard;