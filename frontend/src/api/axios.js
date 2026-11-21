
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
      const org = localStorage.getItem("devinsight.activeOrganization");
      if (org && /^\/api\/v1\/(logs|analyzer)(\/|$)/.test(config.url || "")) {
        config.headers.set("X-DevInsight-Organization", org);
      }
    }

    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const publicEndpoints = [
        "/api/v1/auth/register",
        "/api/v1/auth/login",
      ];
      const isPublic = publicEndpoints.some((url) =>
        error.config?.url?.endsWith(url)
      );

      if (!isPublic) {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        localStorage.removeItem("devinsight.activeOrganization");
        if (!window.location.pathname.startsWith("/login")) {
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(error);
  }
);

export default api;
