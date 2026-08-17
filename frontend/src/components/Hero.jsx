import SearchBar from "./SearchBar";

function Hero({
  username,
  setUsername,
  handleAnalyze,
  error,
  loading
}) {
  return (
    <section className="hero" id="product">
      <div className="hero-eyebrow">
        <span className="status-dot" aria-hidden="true"></span>
        Developer intelligence for engineering teams
      </div>

      <h1 className="hero-title">
        From GitHub activity to hiring insight.
      </h1>

      <p className="hero-subtitle">
        Evaluate repositories, technologies, engineering signals and developer
        experience with one structured report.
      </p>

      <SearchBar
        username={username}
        setUsername={setUsername}
        handleAnalyze={handleAnalyze}
        error={error}
        loading={loading}
      />
    </section>
  );
}

export default Hero;