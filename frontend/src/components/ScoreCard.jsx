function ScoreCard({
  title,
  score
}) {

  return (
    <div className="score-card">

      <h3>{title}</h3>

      <h2 className="score-number">
  {score}
</h2>

    </div>
  );
  const getColor = () => {

  if(score >= 80)
    return "#22c55e";

  if(score >= 60)
    return "#3b82f6";

  if(score >= 40)
    return "#f59e0b";

  return "#ef4444";
};
}

export default ScoreCard;