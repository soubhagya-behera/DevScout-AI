function LoadingSequence({ username }) {
  return (
    <div className="loading-sequence" role="status" aria-live="polite" aria-label="Analyzing GitHub profile">
      <div className="loading-head">
        <div className="loading-title-group">
          <span className="loading-title">
            {username ? (
              <>Analyzing <span className="loading-username">@{username}</span></>
            ) : (
              "Analyzing profile"
            )}
          </span>
          <span className="loading-subtitle">One unified request — collecting evidence, scoring, and AI insights</span>
        </div>
        <span className="loading-spinner" aria-hidden="true" />
      </div>

      <div className="loading-progress loading-progress--indeterminate" aria-hidden="true">
        <div className="loading-progress-fill" />
      </div>

      <p className="loading-hint">This usually takes 6–12 seconds. GitHub evidence is bounded and cached.</p>
    </div>
  );
}

export default LoadingSequence;
