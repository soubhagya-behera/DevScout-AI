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
    <div className="score-card" style={{ animationDelay: `${delay}ms` }}>
      <div className="score-card-top">
        <span className="score-card-title">{title}</span>
        <span className="score-card-value">{value}</span>
      </div>
      <div className="progress-track">
        <div
          className="progress-fill"
          style={{ width: mounted ? `${score}%` : "0%" }}
        />
      </div>
      <div className="score-card-level">{level}</div>
    </div>
  );
}

export default ScoreCard;