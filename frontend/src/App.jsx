import { useState } from "react";
import SearchBar from "./components/SearchBar";
import "./styles/App.css";

function App() {

  const [username, setUsername] =
    useState("");

  const handleAnalyze = () => {

    console.log(username);

  };

  return (
    <div className="app">

      <h1 className="title">
        DevScout AI
      </h1>

      <p className="subtitle">
        AI-Powered GitHub Profile Analyzer
      </p>

      <SearchBar
        username={username}
        setUsername={setUsername}
        handleAnalyze={handleAnalyze}
      />

    </div>
  );
}

export default App;