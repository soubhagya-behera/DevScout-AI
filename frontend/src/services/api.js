import axios from "axios";

const BASE_URL =
  "http://localhost:8080/api/github";

export const getReport =
  async (username) => {

    const response =
      await axios.get(
        `${BASE_URL}/final-report/${username}`
      );

    return response.data;
};

export const getProfile = async (
  username
) => {

  const response =
    await axios.get(
      `http://localhost:8080/api/github/profile/${username}`
    );

  return response.data;
};

export const getRepoAnalysis =
  async (username) => {

    const response =
      await axios.get(
        `http://localhost:8080/api/github/repos/${username}`
      );

    return response.data;
};

