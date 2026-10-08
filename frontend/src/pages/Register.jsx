import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Activity, Loader2 } from "lucide-react";

import { useAuth } from "../context/AuthContext";
import "./Auth.css";

const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();

  const [formData, setFormData] = useState({
    email: "",
    password: "",
    role: "DEVELOPER",
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

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
    setSuccess("");
    setLoading(true);

    try {
      await register(formData);

      setSuccess(
        "Registration successful. You can now sign in."
      );

      setTimeout(() => {
        navigate("/login");
      }, 1000);
    } catch (error) {
      console.error(error);

      setError(
        error.response?.data?.message ||
          "Registration failed. Please try again."
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

        {/* Register Card */}

        <div className="auth-card">
          <h1>Create your account</h1>

          <p className="auth-description">
            Start monitoring and analyzing your
            application logs with DevInsight.
          </p>

          {error && (
            <div className="auth-error">
              {error}
            </div>
          )}

          {success && (
            <div className="auth-success">
              {success}
            </div>
          )}

          <form
            className="auth-form"
            onSubmit={handleSubmit}
            autoComplete="off"
          >

            {/* Email */}

            <div className="auth-field">
              <label htmlFor="register-email">
                Email
              </label>

              <input
                id="register-email"
                name="email"
                type="email"
                autoComplete="email"
                value={formData.email}
                onChange={handleChange}
                placeholder="Enter your email"
                required
              />
            </div>

            {/* Password */}

            <div className="auth-field">
              <label htmlFor="register-password">
                Password
              </label>

              <input
                id="register-password"
                name="password"
                type="password"
                autoComplete="new-password"
                value={formData.password}
                onChange={handleChange}
                placeholder="Create a password"
                required
              />
            </div>

            {/* Role */}

            <div className="auth-field">
              <label htmlFor="role">
                Role
              </label>

              <select
                id="role"
                name="role"
                value={formData.role}
                onChange={handleChange}
              >
                <option value="DEVELOPER">
                  Developer
                </option>

                <option value="DEVOPS">
                  DevOps
                </option>
              </select>
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

                  Creating account...
                </>
              ) : (
                "Create Account"
              )}
            </button>

          </form>

          {/* Footer */}

          <div className="auth-footer">
            Already have an account?{" "}

            <button
              type="button"
              onClick={() => navigate("/login")}
            >
              Sign in
            </button>
          </div>

        </div>
      </div>
    </div>
  );
};

export default Register;