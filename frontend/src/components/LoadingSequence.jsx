const STEPS = [
  { id: "profile", label: "Fetching GitHub profile" },
  { id: "repos", label: "Analyzing repositories" },
  { id: "signals", label: "Calculating engineering signals" },
  { id: "insights", label: "Generating hiring insights" },
];

function CheckIcon() {
  return (
    <svg
      width="12"
      height="12"
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

function DotsIcon() {
  return (
    <span className="step-dots" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </span>
  );
}

function LoadingSequence({ phase, username }) {
  const percent = Math.min(100, phase * 25);

  return (
    <div className="loading-sequence" role="status" aria-live="polite">
      <div className="loading-head">
        <span className="loading-title">
          Analyzing{" "}
          {username ? (
            <span className="loading-username">@{username}</span>
          ) : (
            "profile"
          )}
        </span>
        <span className="loading-percent">{percent}%</span>
      </div>

      <ul className="loading-steps">
        {STEPS.map((step, index) => {
          const state =
            index < phase ? "done" : index === phase ? "active" : "pending";
          return (
            <li
              key={step.id}
              className={`loading-step ${state}`}
            >
              <span className="step-icon">
                {state === "done" && <CheckIcon />}
                {state === "active" && <DotsIcon />}
              </span>
              <span className="step-label">{step.label}</span>
            </li>
          );
        })}
      </ul>

      <div className="loading-progress">
        <div
          className="loading-progress-fill"
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  );
}

export default LoadingSequence;