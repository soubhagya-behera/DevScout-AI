function RepositoryCard({
  name,
  analysis
}) {
  return (
    <div className="repo-card">

      <h3>
        {name}
      </h3>

      <p>
        {analysis}
      </p>

    </div>
  );
}

export default RepositoryCard;