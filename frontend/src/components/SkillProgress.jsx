function SkillProgress({
  title,
  score
}) {

  return (

    <div className="skill-progress">

      <div className="skill-header">

        <span>{title}</span>

        <span>{score}%</span>

      </div>

      <div className="progress-track">

        <div
          className="progress-fill"
          style={{
            width: `${score}%`
          }}
        ></div>

      </div>

    </div>

  );
}

export default SkillProgress;