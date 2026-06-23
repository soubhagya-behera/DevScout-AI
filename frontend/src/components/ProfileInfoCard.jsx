function ProfileInfoCard({
  profile
}) {

  return (

    <div className="profile-info-card">

      <h2>
        GitHub Profile
      </h2>

      <p>
        <strong>👤 Username:</strong>
        {" "}
        {profile.username}
      </p>

      <p>
        <strong>📁 Repositories:</strong>
        {" "}
        {profile.totalRepositories}
      </p>

      <p>
        <strong>💻 Primary Language:
</strong>
        {" "}
        {profile.primaryLanguage}
      </p>

    </div>

  );
}

export default ProfileInfoCard;