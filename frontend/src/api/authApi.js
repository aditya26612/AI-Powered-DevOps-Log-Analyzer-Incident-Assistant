import api from "./axios";

export const register = async (userData) => {
  const response = await api.post("/api/v1/auth/register", userData);
  return response.data;
};

export const login = async (credentials) => {
  const response = await api.post("/api/v1/auth/login", credentials);
  return response.data;
};

export const getCurrentUser = async () => {
  const response = await api.get("/api/v1/users/me");
  return response.data;
};