function AnalysisCard({ analysis }) {

  return (

    <div className="analysis-card">

      <h2>
        AI Candidate Analysis
      </h2>

      <pre>
        {analysis}
      </pre>

    </div>

  );
}

export default AnalysisCard;