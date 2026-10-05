import { useEffect, useRef, useState } from "react";

// Presentational workflow only — NOT backend progress. Durations are tuned
// so the sequence holds gracefully on slow requests and App unmounts this
// component (after a brief "Report ready" state) on success.
const STAGES = [
  {
    id: "prepare",
    label: "Preparing GitHub profile",
    detail: "Connecting to GitHub evidence",
    duration: 800,
  },
  {
    id: "collect",
    label: "Collecting repositories",
    detail: "Discovering public repositories",
    duration: 1500,
  },
  {
    id: "analyze",
    label: "Analyzing engineering evidence",
    detail: "Inspecting repository signals",
    duration: 3400,
  },
  {
    id: "score",
    label: "Calculating engineering scores",
    detail: "Scoring backend, frontend, database and AI",
    duration: 1500,
  },
  {
    id: "insight",
    label: "Generating AI hiring insight",
    detail: "Synthesizing hiring signals",
    duration: 2000,
  },
  {
    id: "build",
    label: "Building structured report",
    detail: "Assembling the final report",
    duration: Infinity,
  },
];

// Visual progress targets per active stage; capped well below 100 while pending.
const PROGRESS_TARGETS = [8, 22, 45, 65, 80, 90];
const PENDING_CAP = 90;

function CheckIcon() {
  return (
    <svg
      className="stage-check"
      width="11"
      height="11"
      viewBox="0 0 12 12"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M2.2 6.4 5 9 9.8 3.4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function LoadingSequence({ username, phase = "active" }) {
  const ready = phase === "ready";
  const [activeIndex, setActiveIndex] = useState(0);
  const [progress, setProgress] = useState(4);
  const activeRef = useRef(0);

  // Sequential stage advancement; holds on the final stage while pending.
  useEffect(() => {
    if (ready) return undefined;
    const pending = [];
    let index = 0;
    const advance = () => {
      index += 1;
      if (index >= STAGES.length - 1) {
        index = STAGES.length - 1;
        activeRef.current = index;
        setActiveIndex(index);
        return;
      }
      activeRef.current = index;
      setActiveIndex(index);
      pending.push(window.setTimeout(advance, STAGES[index].duration));
    };
    pending.push(window.setTimeout(advance, STAGES[0].duration));
    return () => {
      pending.forEach((t) => window.clearTimeout(t));
    };
  }, [ready]);

  // Eased presentational progress; never 100 while the request is pending.
  useEffect(() => {
    const id = window.setInterval(() => {
      setProgress((current) => {
        const target = ready ? 100 : PROGRESS_TARGETS[activeRef.current];
        const next = current + (target - current) * (ready ? 0.35 : 0.06);
        if (ready && 100 - next < 0.6) return 100;
        if (!ready && next > PENDING_CAP) return PENDING_CAP;
        return next;
      });
    }, 120);
    return () => window.clearInterval(id);
  }, [ready]);

  const percent = Math.min(100, Math.round(progress));
  const activeStage = STAGES[ready ? STAGES.length - 1 : activeIndex];

  return (
    <div
      className={`loading-sequence${ready ? " loading-ready" : ""}`}
      role="status"
      aria-live="polite"
      aria-label={ready ? "Report ready" : "Analyzing GitHub profile"}
    >
      <div className="loading-head">
        <div className="loading-title-group">
          <span className="loading-title">
            {ready ? (
              <>Report ready</>
            ) : username ? (
              <>Analyzing <span className="loading-username">@{username}</span></>
            ) : (
              "Analyzing profile"
            )}
          </span>
          <span className="loading-subtitle">
            {ready
              ? "Your developer intelligence report is ready"
              : activeStage.detail}
          </span>
        </div>
        <span className="loading-percent" aria-hidden="true">{percent}%</span>
      </div>

      <ol className="loading-stages">
        {STAGES.map((stage, index) => {
          const status = ready || index < activeIndex
            ? "done"
            : index === activeIndex && !ready
              ? "active"
              : "upcoming";
          return (
            <li
              key={stage.id}
              className={`loading-stage loading-stage--${status}`}
              aria-current={status === "active" ? "step" : undefined}
            >
              <span className="stage-indicator" aria-hidden="true">
                {status === "done" && <CheckIcon />}
              </span>
              <span className="loading-stage-text">
                <span className="loading-stage-label">{stage.label}</span>
                {status === "active" && (
                  <span className="loading-stage-detail">{stage.detail}</span>
                )}
              </span>
            </li>
          );
        })}
      </ol>

      <div
        className="loading-progress"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label="Analysis progress"
      >
        <div
          className="loading-progress-fill"
          style={{ width: `${percent}%` }}
        />
      </div>

      <p className="loading-hint">This usually takes 6–12 seconds. GitHub evidence is bounded and cached.</p>
    </div>
  );
}

export default LoadingSequence;
