function AIInsights({ analysis }) {

  const level =
    analysis.match(
      /LEVEL:(.*?)TOP_STRENGTHS:/s
    )?.[1]?.trim() || "Not Found";

  const strengths =
    analysis.match(
      /TOP_STRENGTHS:([\s\S]*?)IMPROVEMENTS:/i
    )?.[1]
      ?.split("\n")
      ?.filter(line =>
        line.trim().startsWith("-")
      )
      ?.map(line =>
        line.replace("-", "").trim()
      ) || [];

  const improvements =
    analysis.match(
      /IMPROVEMENTS:([\s\S]*?)HIRING_RECOMMENDATION:/i
    )?.[1]
      ?.split("\n")
      ?.filter(line =>
        line.trim().startsWith("-")
      )
      ?.map(line =>
        line.replace("-", "").trim()
      ) || [];

  const hiring =
    analysis.match(
      /HIRING_RECOMMENDATION:(.*)/s
    )?.[1]?.trim() || "Not Found";

  return (

    <div className="ai-dashboard">

      <div className="summary-box level-box">
        <h3>🎯 Level</h3>
        <p>{level}</p>
      </div>

      <div className="summary-box">

        <h3>🚀 Strengths</h3>

        {strengths.map(item => (
          <div
            key={item}
            className="tag success"
          >
            ✓ {item}
          </div>
        ))}

      </div>

      <div className="summary-box">

        <h3>📈 Improvements</h3>

        {improvements.map(item => (
          <div
            key={item}
            className="tag warning"
          >
            {item}
          </div>
        ))}

      </div>

      <div className="summary-box">

        <h3>💼 Hiring Recommendation</h3>

<div className="hire-status">
  Recommended
</div>

<p>{hiring}</p>

      </div>

    </div>

  );
}

export default AIInsights;