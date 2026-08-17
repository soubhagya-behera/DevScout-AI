function SearchBar({
  username,
  setUsername,
  handleAnalyze,
  error,
  loading
}) {
  return (
    <div className="search-area">
      <form
        className={`search-form ${error ? "has-error" : ""}`}
        onSubmit={(event) => {
          event.preventDefault();
          handleAnalyze();
        }}
        noValidate
      >
        <div className="search-input-wrap">
          <span className="search-prefix">github.com/</span>
          <input
            type="text"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="username"
            aria-label="GitHub username"
            spellCheck="false"
            autoComplete="off"
            disabled={loading}
          />
        </div>

        <button
          type="submit"
          className="btn btn-primary analyze-btn"
          disabled={loading}
        >
          Analyze profile <span aria-hidden="true">&rarr;</span>
        </button>
      </form>

      {error && (
        <div className="field-error" role="alert">
          {error}
        </div>
      )}
    </div>
  );
}

export default SearchBar;