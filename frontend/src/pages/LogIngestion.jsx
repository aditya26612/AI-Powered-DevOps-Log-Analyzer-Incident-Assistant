import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Upload,
} from "lucide-react";

import { ingestLog } from "../api/logApi";
import "./LogIngestion.css";

const LogIngestion = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    rawLog: "",
    source: "",
    environment: "",
    applicationName: "",
    hostName: "",
    correlationId: "",
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
      const response = await ingestLog(formData);

        console.log("Log created:", response);

        navigate("/dashboard", {
          state: {
            successMessage: `Log created successfully. ID: ${response.id}`,
          },
        });
    } catch (error) {
      console.error(error);

      setError(
        error.response?.data?.message ||
          "Failed to ingest log. Please try again."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="log-ingestion-page">

      {/* =========================================
          BACK
          ========================================= */}

      <button
        className="analysis-back"
        onClick={() => navigate("/dashboard")}
      >
        <ArrowLeft size={15} />
        Back to Dashboard
      </button>

      {/* =========================================
          HEADER
          ========================================= */}

      

      {/* =========================================
          SUCCESS
          ========================================= */}

      {success && (
        <div className="ingestion-success">
          <CheckCircle2 size={17} />

          <div>
            <strong>Log ingested successfully</strong>
            <div>{success}</div>
          </div>
        </div>
      )}

      {/* =========================================
          ERROR
          ========================================= */}

      {error && (
        <div className="ingestion-error">
          <AlertCircle size={17} />

          <span>{error}</span>
        </div>
      )}

      {/* =========================================
          FORM
          ========================================= */}

      <section className="ingestion-card">

        <form onSubmit={handleSubmit}>

          <div className="ingestion-grid">

            {/* =====================================
                RAW LOG
                ===================================== */}

            <div className="ingestion-field full-width">

              <div className="raw-log-header">

                <label htmlFor="rawLog">
                  Raw Log
                </label>

                <span className="raw-log-hint">
                  Required
                </span>

              </div>

              <textarea
                id="rawLog"
                name="rawLog"
                value={formData.rawLog}
                onChange={handleChange}
                placeholder={
                  "2026-10-07T20:15:32.123+05:30 ERROR 12345 --- [main] com.project.service.PaymentService : Database connection failed"
                }
                required
              />

              <span className="raw-log-help">
                Paste a valid application log entry.
                DevInsight will parse and analyze it.
              </span>

            </div>

            {/* =====================================
                SOURCE
                ===================================== */}

            <div className="ingestion-field">

              <label htmlFor="source">
                Log Source
              </label>

              <select
                id="source"
                name="source"
                value={formData.source}
                onChange={handleChange}
                required
              >
                <option value="">
                  Select source
                </option>

                <option value="SPRING_BOOT">
                  Spring Boot
                </option>

                <option value="DOCKER">
                  Docker
                </option>

                <option value="NGINX">
                  Nginx
                </option>

                <option value="KUBERNETES">
                  Kubernetes
                </option>

                <option value="APACHE">
                  Apache
                </option>

                <option value="JENKINS">
                  Jenkins
                </option>

                <option value="KAFKA">
                  Kafka
                </option>

                <option value="REDIS">
                  Redis
                </option>

                <option value="MYSQL">
                  MySQL
                </option>

                <option value="SYSTEM">
                  System
                </option>

                <option value="CUSTOM">
                  Custom
                </option>
              </select>

            </div>

            {/* =====================================
                ENVIRONMENT
                ===================================== */}

            <div className="ingestion-field">

              <label htmlFor="environment">
                Environment
              </label>

              <select
                id="environment"
                name="environment"
                value={formData.environment}
                onChange={handleChange}
                required
              >
                <option value="">
                  Select environment
                </option>

                <option value="DEVELOPMENT">
                  Development
                </option>

                <option value="TESTING">
                  Testing
                </option>

                <option value="QA">
                  QA
                </option>

                <option value="STAGING">
                  Staging
                </option>

                <option value="PRODUCTION">
                  Production
                </option>
              </select>

            </div>

            {/* =====================================
                APPLICATION
                ===================================== */}

            <div className="ingestion-field">

              <label htmlFor="applicationName">
                Application Name
              </label>

              <input
                id="applicationName"
                name="applicationName"
                value={formData.applicationName}
                onChange={handleChange}
                placeholder="e.g. payment-service"
                required
              />

            </div>

            {/* =====================================
                HOST
                ===================================== */}

            <div className="ingestion-field">

              <label
                htmlFor="hostName"
                className="optional-label"
              >
                Host Name

                <span className="optional-badge">
                  Optional
                </span>
              </label>

              <input
                id="hostName"
                name="hostName"
                value={formData.hostName}
                onChange={handleChange}
                placeholder="e.g. app-server-01"
              />

            </div>

            {/* =====================================
                CORRELATION ID
                ===================================== */}

            <div className="ingestion-field">

              <label
                htmlFor="correlationId"
                className="optional-label"
              >
                Correlation ID

                <span className="optional-badge">
                  Optional
                </span>
              </label>

              <input
                id="correlationId"
                name="correlationId"
                value={formData.correlationId}
                onChange={handleChange}
                placeholder="e.g. TXN-12345"
              />

            </div>

          </div>

          {/* =====================================
              EXAMPLE
              ===================================== */}

          <div className="ingestion-example">

            <div className="ingestion-example-title">
              SUPPORTED SPRING BOOT LOG FORMAT
            </div>

            <pre>
{`2026-10-07T20:15:32.123+05:30 ERROR 12345 --- [main] com.project.service.PaymentService : Database connection failed`}
            </pre>

          </div>

          {/* =====================================
              ACTIONS
              ===================================== */}

          <div className="ingestion-actions">

            <button
              type="button"
              className="ingestion-cancel"
              onClick={() => navigate("/dashboard")}
            >
              Cancel
            </button>

            <button
              type="submit"
              className="ingestion-submit"
              disabled={loading}
            >
              <Upload size={15} />

              {loading
                ? "Ingesting..."
                : "Ingest Log"}
            </button>

          </div>

        </form>

      </section>

    </div>
  );
};

export default LogIngestion;