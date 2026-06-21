function ScoreCard({
  title,
  score
}) {

  return (
    <div className="score-card">

      <h3>{title}</h3>

      <h2>{score}</h2>

    </div>
  );
}

export default ScoreCard;