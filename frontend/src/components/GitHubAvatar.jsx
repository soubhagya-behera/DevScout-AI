import { useState } from "react";

function resolveAvatarSrc(username, avatarUrl) {
  const direct =
    typeof avatarUrl === "string" ? avatarUrl.trim() : "";
  if (direct) return direct;
  const name =
    typeof username === "string" ? username.trim() : "";
  if (name)
    return `https://github.com/${encodeURIComponent(name)}.png`;
  return null;
}

function GitHubAvatar({ username, avatarUrl }) {
  const displayName =
    (typeof username === "string" && username.trim()) ||
    "GitHub user";
  const initial = displayName.charAt(0).toUpperCase() || "?";
  const src = resolveAvatarSrc(username, avatarUrl);
  const [failedSrc, setFailedSrc] = useState(null);
  const failed = failedSrc !== null && failedSrc === src;

  if (!src || failed) {
    return (
      <div
        className="github-avatar"
        role="img"
        aria-label={`${displayName} GitHub profile`}
      >
        <span className="github-avatar-fallback" aria-hidden="true">
          {initial}
        </span>
      </div>
    );
  }

  return (
    <div className="github-avatar">
      <img
        className="github-avatar-img"
        src={src}
        alt={`${displayName} GitHub profile`}
        loading="lazy"
        decoding="async"
        draggable={false}
        onError={() => {
          if (import.meta.env.DEV) {
            console.warn("[GitHubAvatar] image failed", {
              username: displayName,
              src,
            });
          }
          setFailedSrc(src);
        }}
      />
    </div>
  );
}

export default GitHubAvatar;
