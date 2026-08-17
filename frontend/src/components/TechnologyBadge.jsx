function TechnologyBadge({ tech, count }) {
  return (
    <span className="tech-badge">
      <span className="tech-name">{tech}</span>
      {count != null && <span className="tech-count">{count}</span>}
    </span>
  );
}

export default TechnologyBadge;