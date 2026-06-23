function SearchBar({
  username,
  setUsername,
  handleAnalyze
}) {
  return (
    <div className="search-container">

      <input
        type="text"
        placeholder="Enter GitHub Username"
        value={username}
        onChange={(e) =>
          setUsername(e.target.value)
        }
      />

      <button
  className="analyze-btn"
  onClick={handleAnalyze}
>
  ⚡ Analyze Profile
</button>

    </div>
  );
}

export default SearchBar;