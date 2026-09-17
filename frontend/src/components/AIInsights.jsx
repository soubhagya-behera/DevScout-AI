import { parseAiAnalysis } from "../services/parseAiAnalysis";

function CheckIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M3 8.5L6.5 12L13 4.5"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function ArrowIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M8 3.5V12.5M8 12.5L11.5 9M8 12.5L4.5 9"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function AIInsights({ analysis }) {
  const parsed = parseAiAnalysis(analysis);

  const level = parsed.level || "Not found";
  const strengths = parsed.strengths;
  const improvements = parsed.improvements;
  const hiring = parsed.hiring || "Not found";

  const isFallback = analysis && (analysis.includes("temporarily unavailable") || analysis.includes("Deterministic analysis"));

  return (
    <div className="insights">
      {isFallback && (
        <div className="ai-fallback-notice" role="note">
          AI insights temporarily unavailable — showing deterministic fallback based on detected evidence.
        </div>
      )}
      <div className="panel insights-level">
        <span className="insights-label">AI-assessed level</span>
        <span className="level-tag level-tag-large" title="AI interpretation of GitHub evidence, not a verified fact">{level}</span>
      </div>

      <div className="insights-grid">
        <div className="panel insight-list-panel">
          <h4 className="insight-list-title">Top strengths</h4>
          {strengths.length > 0 ? (
            <ul className="insight-list">
              {strengths.map((item) => (
                <li key={item} className="insight-item success">
                  <span className="insight-icon">
                    <CheckIcon />
                  </span>
                  {item}
                </li>
              ))}
            </ul>
          ) : (
            <p className="insight-empty">No strengths detected.</p>
          )}
        </div>

        <div className="panel insight-list-panel">
          <h4 className="insight-list-title">Suggested improvements</h4>
          {improvements.length > 0 ? (
            <ul className="insight-list">
              {improvements.map((item) => (
                <li key={item} className="insight-item warning">
                  <span className="insight-icon">
                    <ArrowIcon />
                  </span>
                  {item}
                </li>
              ))}
            </ul>
          ) : (
            <p className="insight-empty">No improvements detected.</p>
          )}
        </div>
      </div>

      <div className="panel hiring-callout">
        <span className="hiring-status">
          <CheckIcon /> Recommended
        </span>
        <p className="hiring-text">{hiring}</p>
      </div>
    </div>
  );
}

export default AIInsights;