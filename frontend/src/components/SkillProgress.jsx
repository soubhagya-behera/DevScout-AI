import { useEffect, useState } from "react";
import { useCountUp } from "../hooks/useCountUp";

function SkillProgress({ title, score, detail, delay = 0 }) {
  const value = useCountUp(score, { duration: 900, delay });
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setMounted(true), delay);
    return () => clearTimeout(timer);
  }, [delay]);

  return (
    <div className="skill-progress" style={{ animationDelay: `${delay}ms` }}>
      <div className="skill-header">
        <span className="skill-title">{title}</span>
        <span className="skill-detail">
          {detail != null && (
            <span className="skill-detail-text">{detail}</span>
          )}
          <span className="skill-value">{value}</span>
        </span>
      </div>
      <div className="progress-track">
        <div
          className="progress-fill"
          style={{ width: mounted ? `${score}%` : "0%" }}
        />
      </div>
    </div>
  );
}

export default SkillProgress;