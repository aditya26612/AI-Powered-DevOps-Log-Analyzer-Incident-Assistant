import { useState } from "react";
import { login as loginApi, register as registerApi } from "../api/authApi";
import { AuthContext } from "./auth";

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => {
    return localStorage.getItem("token");
  });

  const [user, setUser] = useState(() => {
    const storedUser = localStorage.getItem("user");
    try {
      return storedUser ? JSON.parse(storedUser) : null;
    } catch {
      return null;
    }
  });

  const login = async (credentials) => {
    const response = await loginApi(credentials);

    localStorage.setItem("token", response.token);

    const userData = {
      email: response.email,
      role: response.role,
    };

    localStorage.setItem("user", JSON.stringify(userData));

    setToken(response.token);
    setUser(userData);

    return response;
  };

  const register = async (userData) => {
    return await registerApi(userData);
  };

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    localStorage.removeItem("devinsight.activeOrganization");

    setToken(null);
    setUser(null);
  };

  const isAuthenticated = Boolean(token);

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isAuthenticated,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
