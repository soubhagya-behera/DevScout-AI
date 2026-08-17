function extractBullets(section) {
  if (!section) return [];
  return section
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => line.replace(/^[-•*]\s*/, "").trim())
    .filter((line) => line.length > 0);
}

function pick(first, second) {
  if (first && first.trim()) return first.trim();
  return second && second.trim() ? second.trim() : "";
}

export function parseAiAnalysis(analysis) {
  if (!analysis || typeof analysis !== "string") {
    return { level: "", strengths: [], improvements: [], hiring: "" };
  }

  const text = analysis;

  const levelA = text.match(/LEVEL:\s*([\s\S]*?)TOP_STRENGTHS:/i)?.[1];
  const strengthsA = extractBullets(
    text.match(/TOP_STRENGTHS:\s*([\s\S]*?)IMPROVEMENTS:/i)?.[1]
  );
  const improvementsA = extractBullets(
    text.match(/IMPROVEMENTS:\s*([\s\S]*?)HIRING_RECOMMENDATION:/i)?.[1]
  );
  const hiringA = text.match(/HIRING_RECOMMENDATION:\s*([\s\S]*)/i)?.[1];

  const levelB = text.match(
    /Skill Level:\s*([\s\S]*?)(?:Strengths:|Areas? for Improvement:|Hiring Recommendation:)/i
  )?.[1];
  const strengthsB = extractBullets(
    text.match(
      /Strengths:\s*([\s\S]*?)(?:Areas? for Improvement:|Hiring Recommendation:)/i
    )?.[1]
  );
  const improvementsB = extractBullets(
    text.match(
      /Areas? for Improvement:\s*([\s\S]*?)(?:Hiring Recommendation:)/i
    )?.[1]
  );
  const hiringB = text.match(/Hiring Recommendation:\s*([\s\S]*)/i)?.[1];

  return {
    level: pick(levelA, levelB),
    strengths: strengthsA.length > 0 ? strengthsA : strengthsB,
    improvements: improvementsA.length > 0 ? improvementsA : improvementsB,
    hiring: pick(hiringA, hiringB),
  };
}