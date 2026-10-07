import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ingestLog } from "../api/logApi";

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

      setSuccess(`Log created successfully. ID: ${response.id}`);
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
    <div>
      <button onClick={() => navigate("/dashboard")}>
        ← Back to Dashboard
      </button>

      <h1>Ingest Log</h1>

      <form onSubmit={handleSubmit}>
        {/* Raw Log */}
        <div>
          <label htmlFor="rawLog">Raw Log</label>

          <textarea
            id="rawLog"
            name="rawLog"
            value={formData.rawLog}
            onChange={handleChange}
            placeholder="Enter application log..."
            rows="8"
            required
          />
        </div>

        {/* Source */}
        <div>
          <label htmlFor="source">Source</label>

          <select
            id="source"
            name="source"
            value={formData.source}
            onChange={handleChange}
            required
          >
            <option value="">Select log source</option>
            <option value="SPRING_BOOT">SPRING_BOOT</option>
            <option value="DOCKER">DOCKER</option>
            <option value="NGINX">NGINX</option>
            <option value="KUBERNETES">KUBERNETES</option>
            <option value="APACHE">APACHE</option>
            <option value="JENKINS">JENKINS</option>
            <option value="KAFKA">KAFKA</option>
            <option value="REDIS">REDIS</option>
            <option value="MYSQL">MYSQL</option>
            <option value="SYSTEM">SYSTEM</option>
            <option value="CUSTOM">CUSTOM</option>
          </select>
        </div>

        {/* Environment */}
        <div>
          <label htmlFor="environment">Environment</label>

          <select
            id="environment"
            name="environment"
            value={formData.environment}
            onChange={handleChange}
            required
          >
            <option value="">Select environment</option>
            <option value="DEVELOPMENT">DEVELOPMENT</option>
            <option value="TESTING">TESTING</option>
            <option value="QA">QA</option>
            <option value="STAGING">STAGING</option>
            <option value="PRODUCTION">PRODUCTION</option>
          </select>
        </div>

        {/* Application Name */}
        <div>
          <label htmlFor="applicationName">Application Name</label>

          <input
            id="applicationName"
            name="applicationName"
            value={formData.applicationName}
            onChange={handleChange}
            placeholder="e.g. payment-service"
            required
          />
        </div>

        {/* Host Name */}
        <div>
          <label htmlFor="hostName">Host Name</label>

          <input
            id="hostName"
            name="hostName"
            value={formData.hostName}
            onChange={handleChange}
            placeholder="Optional"
          />
        </div>

        {/* Correlation ID */}
        <div>
          <label htmlFor="correlationId">Correlation ID</label>

          <input
            id="correlationId"
            name="correlationId"
            value={formData.correlationId}
            onChange={handleChange}
            placeholder="Optional"
          />
        </div>

        {/* Messages */}
        {error && <p>{error}</p>}
        {success && <p>{success}</p>}

        {/* Submit */}
        <button type="submit" disabled={loading}>
          {loading ? "Ingesting..." : "Ingest Log"}
        </button>
      </form>
    </div>
  );
};

export default LogIngestion;