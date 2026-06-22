function CandidateSummary({ analysis }) {
  return (
    <div className="candidate-summary">
      <h2>Candidate Summary</h2>
      <pre>
        {analysis}
      </pre>
    </div>
  );
}
export default CandidateSummary;