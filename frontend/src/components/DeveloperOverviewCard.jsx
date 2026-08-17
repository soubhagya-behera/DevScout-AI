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

function DeveloperOverviewCard({ profile, username, overallScore }) {
  const level =
    overallScore >= 80
      ? "Expert"
      : overallScore >= 60
        ? "Advanced"
        : overallScore >= 40
          ? "Intermediate"
          : "Beginner";

  return (
    <section className="panel overview-panel">
      <div className="overview-main">
        <div className="overview-identity">
          <div className="avatar avatar-xl">
            {username.charAt(0).toUpperCase()}
          </div>
          <div className="overview-identity-text">
            <h3 className="overview-name">{username}</h3>
            <div className="overview-meta">
              GitHub developer profile
            </div>
            <span className="level-tag">{level}</span>
          </div>
        </div>

        <div className="overview-score">
          <ScoreRing value={overallScore} />
          <span className="overview-score-label">Overall score</span>
        </div>
      </div>

      <div className="overview-stats">
        <div className="overview-stat">
          <span className="overview-stat-value">
            {profile?.totalRepositories ?? 0}
          </span>
          <span className="overview-stat-label">Repositories</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">
            {profile?.primaryLanguage ?? "Unknown"}
          </span>
          <span className="overview-stat-label">Primary language</span>
        </div>
        <div className="overview-stat">
          <span className="overview-stat-value">{level}</span>
          <span className="overview-stat-label">Experience level</span>
        </div>
      </div>
    </section>
  );
}

export default DeveloperOverviewCard;