import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { useNavigate } from "react-router-dom";
import { deleteLog, searchLogs } from "../api/logApi";

const Dashboard = () => {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [filters, setFilters] = useState({
    level: "",
    source: "",
    environment: "",
    anomaly: "",
    applicationName: "",
    message: "",
  });

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const loadLogs = async (currentPage = 0, currentFilters = filters) => {
    try {
      setLoading(true);
      setError("");

      const request = {
        level: currentFilters.level || null,

        source: currentFilters.source || null,

        environment: currentFilters.environment || null,

        status: null,

        anomaly:
          currentFilters.anomaly === ""
            ? null
            : currentFilters.anomaly === "true",

        applicationName:
          currentFilters.applicationName.trim() || null,

        serviceName: null,
        loggerName: null,
        threadName: null,
        hostName: null,

        message:
          currentFilters.message.trim() || null,

        correlationId: null,
        startTime: null,
        endTime: null,

        page: currentPage,
        size: 20,

        sortBy: "TIMESTAMP",
        sortDirection: "DESC",
      };

      console.log("Search request:", request);

      const response = await searchLogs(request);

      console.log("Search response:", response);

      setLogs(response.content || []);
      setPage(response.page ?? currentPage);
      setTotalPages(response.totalPages ?? 0);
      setTotalElements(response.totalElements ?? 0);
    } catch (error) {
      console.error("Failed to load logs:", error);

      setError(
        error.response?.data?.message ||
          "Failed to load logs."
      );
    } finally {
      setLoading(false);
    }
  };

  // Initial dashboard load
  useEffect(() => {
  let cancelled = false;

  const fetchInitialLogs = async () => {
    try {
      setLoading(true);
      setError("");

      const request = {
        level: null,
        source: null,
        environment: null,
        status: null,
        anomaly: null,
        applicationName: null,
        serviceName: null,
        loggerName: null,
        threadName: null,
        hostName: null,
        message: null,
        correlationId: null,
        startTime: null,
        endTime: null,
        page: 0,
        size: 20,
        sortBy: "TIMESTAMP",
        sortDirection: "DESC",
      };

      const response = await searchLogs(request);

      if (cancelled) {
        return;
      }

      setLogs(response.content || []);
      setPage(response.page ?? 0);
      setTotalPages(response.totalPages ?? 0);
      setTotalElements(response.totalElements ?? 0);
    } catch (error) {
      if (cancelled) {
        return;
      }

      console.error("Failed to load logs:", error);

      setError(
        error.response?.data?.message ||
          "Failed to load logs."
      );
    } finally {
      if (!cancelled) {
        setLoading(false);
      }
    }
  };

  fetchInitialLogs();

  return () => {
    cancelled = true;
  };
}, []);

  const handleFilterChange = (event) => {
    const { name, value } = event.target;

    setFilters((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSearch = () => {
    loadLogs(0, filters);
  };

  const handleDelete = async (id) => {
    const confirmed = window.confirm(
      `Are you sure you want to delete Log #${id}?`
    );

    if (!confirmed) {
      return;
    }

    try {
      setError("");

      await deleteLog(id);

      await loadLogs(page, filters);
    } catch (error) {
      console.error("Failed to delete log:", error);

      setError(
        error.response?.data?.message ||
          "Failed to delete log."
      );
    }
  };

  const handlePrevious = () => {
    if (page > 0) {
      loadLogs(page - 1, filters);
    }
  };

  const handleNext = () => {
    if (page < totalPages - 1) {
      loadLogs(page + 1, filters);
    }
  };

  return (
    <div>
      <header>
        <h1>DevInsight</h1>

        <div>
          <span>{user?.email}</span>{" "}
          <span>{user?.role}</span>{" "}

          <button onClick={logout}>
            Logout
          </button>
        </div>
      </header>

      <main>
        <h2>Dashboard</h2>

        <p>AI-Powered DevOps Analyzer</p>

        <section>
          <h3>Log Analysis</h3>

          <p>
            Ingest and analyze application logs.
          </p>

          <button
            onClick={() => navigate("/logs/ingest")}
          >
            Ingest Log
          </button>
        </section>

        <hr />

        <section>
          <h2>Search Logs</h2>

          {/* Level */}
          <div>
            <label>
              Level:{" "}
              <select
                name="level"
                value={filters.level}
                onChange={handleFilterChange}
              >
                <option value="">All</option>
                <option value="TRACE">TRACE</option>
                <option value="DEBUG">DEBUG</option>
                <option value="INFO">INFO</option>
                <option value="WARN">WARN</option>
                <option value="ERROR">ERROR</option>
              </select>
            </label>
          </div>

          {/* Source */}
          <div>
            <label>
              Source:{" "}
              <select
                name="source"
                value={filters.source}
                onChange={handleFilterChange}
              >
                <option value="">All</option>
                <option value="SPRING_BOOT">
                  SPRING_BOOT
                </option>
                <option value="DOCKER">DOCKER</option>
                <option value="NGINX">NGINX</option>
                <option value="KUBERNETES">
                  KUBERNETES
                </option>
                <option value="APACHE">APACHE</option>
                <option value="JENKINS">JENKINS</option>
                <option value="KAFKA">KAFKA</option>
                <option value="REDIS">REDIS</option>
                <option value="MYSQL">MYSQL</option>
                <option value="SYSTEM">SYSTEM</option>
                <option value="CUSTOM">CUSTOM</option>
              </select>
            </label>
          </div>

          {/* Environment */}
          <div>
            <label>
              Environment:{" "}
              <select
                name="environment"
                value={filters.environment}
                onChange={handleFilterChange}
              >
                <option value="">All</option>
                <option value="DEVELOPMENT">
                  DEVELOPMENT
                </option>
                <option value="TESTING">TESTING</option>
                <option value="QA">QA</option>
                <option value="STAGING">STAGING</option>
                <option value="PRODUCTION">
                  PRODUCTION
                </option>
              </select>
            </label>
          </div>

          {/* Anomaly */}
          <div>
            <label>
              Anomaly:{" "}
              <select
                name="anomaly"
                value={filters.anomaly}
                onChange={handleFilterChange}
              >
                <option value="">All</option>
                <option value="true">
                  Anomalies Only
                </option>
                <option value="false">
                  Normal Only
                </option>
              </select>
            </label>
          </div>

          {/* Application */}
          <div>
            <label>
              Application:{" "}
              <input
                type="text"
                name="applicationName"
                value={filters.applicationName}
                onChange={handleFilterChange}
                placeholder="payment-service"
              />
            </label>
          </div>

          {/* Message */}
          <div>
            <label>
              Message:{" "}
              <input
                type="text"
                name="message"
                value={filters.message}
                onChange={handleFilterChange}
                placeholder="Search message"
              />
            </label>
          </div>

          <button onClick={handleSearch}>
            Search
          </button>
        </section>

        <hr />

        <section>
          <h2>Logs</h2>

          <p>
            Total logs: {totalElements}
          </p>

          {error && <p>{error}</p>}

          {loading && <p>Loading logs...</p>}

          {!loading && logs.length === 0 && (
            <p>No logs found.</p>
          )}

          {!loading &&
            logs.map((log) => (
              <div key={log.id}>
                <hr />

                <h3>
                  Log #{log.id}
                </h3>

                <p>
                  <strong>Time:</strong>{" "}
                  {log.timestamp}
                </p>

                <p>
                  <strong>Level:</strong>{" "}
                  {log.level}
                </p>

                <p>
                  <strong>Application:</strong>{" "}
                  {log.applicationName}
                </p>

                <p>
                  <strong>Source:</strong>{" "}
                  {log.source}
                </p>

                <p>
                  <strong>Environment:</strong>{" "}
                  {log.environment}
                </p>

                <p>
                  <strong>Message:</strong>{" "}
                  {log.message}
                </p>

                <p>
                  <strong>ML Prediction:</strong>{" "}
                  {log.predictionLabel ||
                    "Not analyzed"}
                </p>

                <p>
                  <strong>Analysis Status:</strong>{" "}
                  {log.analysisStatus || "N/A"}
                </p>

                <button
                  onClick={() =>
                    navigate(
                      `/logs/${log.id}/analyze`
                    )
                  }
                >
                  Analyze
                </button>

                <button
                  onClick={() =>
                    handleDelete(log.id)
                  }
                >
                  Delete
                </button>
              </div>
            ))}
        </section>

        {!loading && totalPages > 0 && (
          <section>
            <button
              onClick={handlePrevious}
              disabled={page === 0}
            >
              Previous
            </button>

            <span>
              {" "}
              Page {page + 1} of {totalPages}{" "}
            </span>

            <button
              onClick={handleNext}
              disabled={page >= totalPages - 1}
            >
              Next
            </button>
          </section>
        )}
      </main>
    </div>
  );
};

export default Dashboard;