function ScoreCard({ title, score }) {

  const getLevel = () => {

    if(score >= 80)
      return "Expert";

    if(score >= 60)
      return "Advanced";

    if(score >= 40)
      return "Intermediate";

    return "Beginner";
  };

  const getIcon = () => {

    if(title === "Backend")
      return "⚙️";

    if(title === "Frontend")
      return "🎨";

    if(title === "Database")
      return "🗄️";

    if(title === "AI")
      return "🤖";

    return "📊";
  };

  return (

    <div className="score-card">

      <h3>
        {getIcon()} {title}
      </h3>

      <div className="score-circle">
        {score}
      </div>

      <div className="score-level">
        {getLevel()}
      </div>

    </div>
  );
}

export default ScoreCard;