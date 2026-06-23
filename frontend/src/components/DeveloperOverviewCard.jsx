function DeveloperOverviewCard({
  profile,
  username,
  overallScore
}) {

  let level = "Beginner";

  if (overallScore >= 80)
    level = "Expert";
  else if (overallScore >= 60)
    level = "Advanced";
  else if (overallScore >= 40)
    level = "Intermediate";

  return (
    <div className="developer-overview-card">

      <div className="overview-top">

        <div className="overview-avatar">
          {username.charAt(0).toUpperCase()}
        </div>

        <div>
          <h2>{username}</h2>

          <p className="overview-level">
            {level} Developer
          </p>
        </div>

      </div>

      <div className="overview-stats">

        <div className="stat-box">
          <h3>{profile.totalRepositories}</h3>
          <span>Repositories</span>
        </div>

        <div className="stat-box">
          <h3>{profile.primaryLanguage}</h3>
          <span>Primary Tech</span>
        </div>

        <div className="stat-box">
          <h3>{overallScore}</h3>
          <span>Overall Score</span>
        </div>

      </div>

    </div>
  );
}

export default DeveloperOverviewCard;