import AIInsights from "../AIInsights";

function InsightsSection({ analysis }) {
  return (
    <div className="section-stack">
      <section className="panel insights-panel">
        <div className="panel-header">
          <h3 className="panel-title">AI hiring insights</h3>
          <span className="panel-note">Generated from the profile</span>
        </div>
        <AIInsights analysis={analysis} />
      </section>
    </div>
  );
}

export default InsightsSection;