function ProfileCard({
  username,
  overallScore
}) {

  let level = "Beginner";

  if (overallScore >= 70)
    level = "Advanced";

  else if (overallScore >= 50)
    level = "Intermediate";

  return (

    <div className="profile-card">

      <div className="profile-avatar">
        {username.charAt(0).toUpperCase()}
      </div>

      <div>

        <h2>{username}</h2>

        <p>
          Developer Level:
          <strong> {level}</strong>
        </p>

      </div>

    </div>

  );
}

export default ProfileCard;