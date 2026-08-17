import { useEffect, useState } from "react";
import { useCountUp } from "../hooks/useCountUp";

const PREVIEW_SKILLS = [
  { title: "Backend", score: 82 },
  { title: "Frontend", score: 64 },
  { title: "Database", score: 71 },
  { title: "AI", score: 55 },
];

const PREVIEW_TECH = [
  "Spring Boot",
  "React",
  "JWT",
  "MySQL",
  "Razorpay",
  "Gemini API",
];

function PreviewBar({ score, delay }) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setMounted(true), delay);
    return () => clearTimeout(timer);
  }, [delay]);

  return (
    <div className="preview-bar">
      <div className="preview-bar-label">
        <span>{score.title}</span>
        <span className="preview-bar-value">{score.score}</span>
      </div>
      <div className="progress-track">
        <div
          className="progress-fill"
          style={{ width: mounted ? `${score.score}%` : "0%" }}
        />
      </div>
    </div>
  );
}

function ProductPreview() {
  const overall = useCountUp(82, { duration: 1200, delay: 600 });

  return (
    <section className="preview-section" id="how-it-works">
      <div className="preview-heading">
        <h2>One structured report, ready to read.</h2>
        <p>
          DevScout distills a public GitHub profile into the signals an
          engineering team actually cares about.
        </p>
      </div>

      <div className="preview" aria-hidden="true">
        <div className="preview-window">
          <div className="preview-sidebar">
            <div className="preview-nav-item active">Overview</div>
            <div className="preview-nav-item">Repositories</div>
            <div className="preview-nav-item">Technologies</div>
            <div className="preview-nav-item">AI Insights</div>
          </div>

          <div className="preview-main">
            <div className="preview-header">
              <div className="preview-user">
                <div className="avatar avatar-lg">S</div>
                <div className="preview-user-text">
                  <div className="preview-name">soubhagya</div>
                  <div className="preview-meta">
                    Full-Stack Developer · github.com/soubhagya
                  </div>
                </div>
              </div>
              <div className="preview-score">
                <span className="preview-score-label">Overall</span>
                <span className="preview-score-value">{overall}</span>
              </div>
            </div>

            <div className="preview-stats">
              <div className="preview-stat">
                <span className="preview-stat-value">24</span>
                <span className="preview-stat-label">Repositories</span>
              </div>
              <div className="preview-stat">
                <span className="preview-stat-value">Java</span>
                <span className="preview-stat-label">Primary language</span>
              </div>
              <div className="preview-stat">
                <span className="preview-stat-value">Advanced</span>
                <span className="preview-stat-label">Experience level</span>
              </div>
            </div>

            <div className="preview-grid">
              <div className="preview-panel">
                <h4>Engineering skill scores</h4>
                {PREVIEW_SKILLS.map((skill, index) => (
                  <PreviewBar
                    key={skill.title}
                    score={skill}
                    delay={700 + index * 120}
                  />
                ))}
              </div>

              <div className="preview-panel">
                <h4>Technology stack</h4>
                <div className="preview-tech-list">
{PREVIEW_TECH.map((tech) => (
                  <span className="tech-badge" key={tech}>
                    {tech}
                  </span>
                ))}
                </div>
                <div className="preview-insight">
                  <span className="preview-insight-label">
                    AI hiring insight
                  </span>
                  <p>
                    Strong backend fundamentals with real payment and auth
                    integration experience. Good fit for full-stack roles.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export default ProductPreview;