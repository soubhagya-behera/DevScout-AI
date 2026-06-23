function CandidateSummary({ analysis }) {

  return (

    <div className="candidate-summary">

      <div className="summary-header">

        🧠 AI Recruiter Analysis

      </div>

      <div className="summary-content">

        {analysis}

      </div>

    </div>
  );
}

export default CandidateSummary;