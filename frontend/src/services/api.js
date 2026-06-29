import axios from "axios";

const BASE_URL =
  "https://devscout-ai-backend.onrender.com/api/github";

export const getReport =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/final-report/${username}`
      );

    return response.data;
};

export const getProfile =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/profile/${username}`
      );

    return response.data;
};

export const getRepoAnalysis =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/repos/${username}`
      );

    return response.data;
};