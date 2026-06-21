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