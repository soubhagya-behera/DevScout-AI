import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  ResponsiveContainer
} from "recharts";

function RadarSkillChart({ backend, frontend, database, ai }) {
  const data = [
    { subject: "Backend", score: backend },
    { subject: "Frontend", score: frontend },
    { subject: "Database", score: database },
    { subject: "AI", score: ai },
  ];

  return (
    <div className="radar-card">
      <ResponsiveContainer width="100%" height={260}>
        <RadarChart data={data} outerRadius="70%">
          <PolarGrid stroke="#262626" />
          <PolarAngleAxis
            dataKey="subject"
            tick={{ fill: "#A1A1AA", fontSize: 12 }}
          />
          <Radar
            dataKey="score"
            stroke="#F5F5F5"
            fill="#F5F5F5"
            fillOpacity={0.06}
            strokeWidth={1.5}
          />
        </RadarChart>
      </ResponsiveContainer>
    </div>
  );
}

export default RadarSkillChart;