import axios from "axios";

const BASE_URL =
  (import.meta.env.VITE_API_BASE_URL || "https://devscout-ai-backend.onrender.com/api/github").replace(/\/$/, "");

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