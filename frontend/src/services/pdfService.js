import { jsPDF } from "jspdf";

export const downloadReport = (
  username,
  report
) => {

  const doc = new jsPDF();

  doc.setFontSize(20);
  doc.text(
    "DevScout AI Report",
    20,
    20
  );

  doc.setFontSize(12);

  doc.text(
    `Username: ${username}`,
    20,
    40
  );

  doc.text(
    `Overall Score: ${report.overallScore}`,
    20,
    50
  );

  doc.text(
    `Backend Score: ${report.backendScore}`,
    20,
    60
  );

  doc.text(
    `Frontend Score: ${report.frontendScore}`,
    20,
    70
  );

  doc.text(
    `Database Score: ${report.databaseScore}`,
    20,
    80
  );

  doc.text(
    `AI Score: ${report.aiScore}`,
    20,
    90
  );

  doc.text(
    "Technologies:",
    20,
    110
  );

  let y = 120;

  Object.keys(
    report.technologies
  ).forEach((tech) => {

    doc.text(
      `• ${tech}`,
      25,
      y
    );

    y += 10;
  });

  y += 10;

doc.setFontSize(16);

doc.text(
  "AI Analysis",
  20,
  y
);

y += 10;

doc.setFontSize(10);

const lines =
  doc.splitTextToSize(
    report.aiAnalysis,
    170
  );

doc.text(
  lines,
  20,
  y
);

  doc.save(
    `${username}_report.pdf`
  );
};