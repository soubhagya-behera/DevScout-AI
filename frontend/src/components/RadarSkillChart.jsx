import { Radar, RadarChart, PolarGrid, PolarAngleAxis, ResponsiveContainer } from "recharts";

function RadarSkillChart({
  backend,
  frontend,
  database,
  ai
}) {

  const data = [
    {
      subject: "Backend",
      score: backend
    },
    {
      subject: "Frontend",
      score: frontend
    },
    {
      subject: "Database",
      score: database
    },
    {
      subject: "AI",
      score: ai
    }
  ];

  return (
    <div className="radar-card">

      <h2>
        Skill Intelligence
      </h2>

      <ResponsiveContainer width="100%" height={280}>
  <RadarChart
    data={data}
    outerRadius="65%"
  >
    <PolarGrid />

    <PolarAngleAxis
      dataKey="subject"
      tick={{ fill: "#cbd5e1", fontSize: 12 }}
    />

    <Radar
      dataKey="score"
      stroke="#8b5cf6"
      fill="#8b5cf6"
      fillOpacity={0.6}
    />
  </RadarChart>
</ResponsiveContainer>

    </div>
  );
}

export default RadarSkillChart;