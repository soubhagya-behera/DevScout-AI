import { useEffect, useState } from "react";
import { useCountUp } from "../hooks/useCountUp";

function ScoreCard({ title, score, delay = 0 }) {
  const value = useCountUp(score, { duration: 900, delay });
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setMounted(true), delay);
    return () => clearTimeout(timer);
  }, [delay]);

  const level =
    score >= 80
      ? "Expert"
      : score >= 60
        ? "Advanced"
        : score >= 40
          ? "Intermediate"
          : "Beginner";

  return (
    <div className="score-card" style={{ animationDelay: `${delay}ms` }} aria-label={`${title} GitHub evidence score ${score} out of 100, ${level}`}>
      <div className="score-card-top">
        <span className="score-card-title">{title}</span>
        <span className="score-card-value" aria-hidden="true">{value}</span>
      </div>
      <div className="progress-track" role="progressbar" aria-valuenow={score} aria-valuemin={0} aria-valuemax={100} aria-label={`${title} score`}>
        <div
          className="progress-fill"
          style={{ width: mounted ? `${score}%` : "0%" }}
        />
      </div>
      <div className="score-card-meta">
        <span className="score-card-level">{level}</span>
        <span className="score-card-hint">GitHub evidence</span>
      </div>
    </div>
  );
}

export default ScoreCard;