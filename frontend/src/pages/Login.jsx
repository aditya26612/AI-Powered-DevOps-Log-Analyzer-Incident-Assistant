import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Activity, Loader2 } from "lucide-react";

import { useAuth } from "../context/AuthContext";
import "./Auth.css";

const Login = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [formData, setFormData] = useState({
    email: "",
    password: "",
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    setError("");
    setLoading(true);

    try {
      await login(formData);

      navigate("/dashboard");
    } catch (error) {
      console.error(error);

      setError(
        error.response?.data?.message ||
          "Invalid email or password."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">

        {/* Brand */}

        <div className="auth-brand">
          <div className="auth-logo">
            <Activity size={24} />
          </div>

          <h2 className="auth-brand-name">
            DevInsight
          </h2>

          <p className="auth-brand-subtitle">
            AI-Powered DevOps Analyzer
          </p>
        </div>

        {/* Login Card */}

        <div className="auth-card">
          <h1>Welcome back</h1>

          <p className="auth-description">
            Sign in to monitor, analyze, and
            investigate your application logs.
          </p>

          {error && (
            <div className="auth-error">
              {error}
            </div>
          )}

          <form
            className="auth-form"
            onSubmit={handleSubmit}
            autoComplete="off"
          >

            {/* Email */}

            <div className="auth-field">
              <label htmlFor="login-email">
                Email
              </label>

              <input
                id="login-email"
                name="email"
                type="email"
                autoComplete="username"
                value={formData.email}
                onChange={handleChange}
                placeholder="Enter your email"
                required
              />
            </div>

            {/* Password */}

            <div className="auth-field">
              <label htmlFor="login-password">
                Password
              </label>

              <input
                id="login-password"
                name="password"
                type="password"
                autoComplete="current-password"
                value={formData.password}
                onChange={handleChange}
                placeholder="Enter your password"
                required
              />
            </div>

            {/* Submit */}

            <button
              type="submit"
              className="auth-submit"
              disabled={loading}
            >
              {loading ? (
                <>
                  <Loader2
                    size={15}
                    className="loading-spinner"
                  />

                  Signing in...
                </>
              ) : (
                "Sign In"
              )}
            </button>

          </form>

          {/* Footer */}

          <div className="auth-footer">
            Don't have an account?{" "}

            <button
              type="button"
              onClick={() => navigate("/register")}
            >
              Create one
            </button>
          </div>

        </div>
      </div>
    </div>
  );
};

export default Login;