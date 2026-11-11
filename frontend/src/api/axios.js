
import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");

    // Public endpoints should not receive an old JWT.
    const publicEndpoints = [
      "/api/v1/auth/register",
      "/api/v1/auth/login",
    ];

    const isPublicEndpoint = publicEndpoints.some(
      (endpoint) => config.url?.endsWith(endpoint)
    );

    if (isPublicEndpoint) {
      config.headers.delete("Authorization");
    } else if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

export default api;
