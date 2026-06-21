function TechnologyBadge({
  tech,
  count
}) {
  return (
    <div className="tech-badge">
      <span>{tech}</span>
      <span className="tech-count">
        {count}
      </span>
    </div>
  );
}

export default TechnologyBadge;