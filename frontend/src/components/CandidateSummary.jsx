function CandidateSummary({ analysis }) {
  return (
    <div className="candidate-summary">
      <h2>Candidate Summary</h2>
      <div className="summary-content">
  {analysis}
</div>
    </div>
  );
}
export default CandidateSummary;