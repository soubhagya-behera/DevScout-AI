import axios from "axios";

const API_PATH = "/api/github";

function resolveBaseUrl() {
  const configured = (import.meta.env.VITE_API_BASE_URL || "")
    .trim()
    .replace(/\/$/, "");
  if (configured) {
    return configured.endsWith(API_PATH)
      ? configured
      : `${configured}${API_PATH}`;
  }
  if (import.meta.env.DEV) {
    throw new Error(
      "VITE_API_BASE_URL is not set. Add it to frontend/.env.development for local development."
    );
  }
  return `https://devscout-ai-backend.onrender.com${API_PATH}`;
}

const BASE_URL = resolveBaseUrl();

export const getUnifiedReport =
  async (username) => {
    const response =
      await axios.get(
        `${BASE_URL}/report/${encodeURIComponent(username.trim())}`
      );
    return response.data;
};

export const getReport =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/final-report/${encodeURIComponent(username.trim())}`
      );

    return response.data;
};

export const getProfile =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/profile/${encodeURIComponent(username.trim())}`
      );

    return response.data;
};

export const getAnalyze =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/analyze/${encodeURIComponent(username.trim())}`
      );

    return response.data;
};

export const getRepoAnalysis =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/full-analysis/${encodeURIComponent(username.trim())}`
      );

    return response.data;
};