import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import {
  Brain,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  FileText,
  Search,
  Trash2,
  Upload,
} from "lucide-react";

import { deleteLog, searchLogs } from "../api/logApi";
import "./Dashboard.css";

const Dashboard = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [successMessage, setSuccessMessage] = useState(
    location.state?.successMessage || ""
  );

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

  // =========================================================
  // SUCCESS MESSAGE FROM INGESTION
  // =========================================================

  useEffect(() => {
    if (location.state?.successMessage) {
      setSuccessMessage(location.state.successMessage);

      navigate(location.pathname, {
        replace: true,
        state: null,
      });
    }
  }, [location, navigate]);

  // =========================================================
  // LOAD LOGS
  // =========================================================

  const loadLogs = async (
    currentPage = 0,
    currentFilters = filters
  ) => {
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

      const response = await searchLogs(request);

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

  // =========================================================
  // INITIAL DASHBOARD LOAD
  // =========================================================

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

  // =========================================================
  // FILTERS
  // =========================================================

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

  // =========================================================
  // DELETE
  // =========================================================

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

  // =========================================================
  // PAGINATION
  // =========================================================

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

  // =========================================================
  // DASHBOARD STATISTICS
  // =========================================================

  const errorCount = logs.filter(
    (log) => log.level === "ERROR"
  ).length;

  const warningCount = logs.filter(
    (log) => log.level === "WARN"
  ).length;

  const anomalyCount = logs.filter(
    (log) => log.anomaly === true
  ).length;

  const analyzedCount = logs.filter(
    (log) => log.llmAnalysisStatus === "COMPLETED"
  ).length;

  // =========================================================
  // HELPERS
  // =========================================================

  const getLevelClass = (level) => {
    switch (level) {
      case "ERROR":
        return "error";

      case "WARN":
        return "warn";

      case "INFO":
        return "info";

      case "DEBUG":
        return "debug";

      case "TRACE":
        return "trace";

      default:
        return "unknown";
    }
  };

  const getAnalysisClass = (status) => {
    if (!status) {
      return "unknown";
    }

    switch (status) {
      case "COMPLETED":
        return "completed";

      case "FAILED":
        return "failed";

      case "PENDING":
      case "PROCESSING":
        return "pending";

      default:
        return "unknown";
    }
  };

  const formatTimestamp = (timestamp) => {
    if (!timestamp) {
      return "Unknown time";
    }

    return timestamp.replace("T", " ");
  };

  // =========================================================
  // UI
  // =========================================================

  return (
    <div className="dashboard-page">

      {/* =====================================================
          SUCCESS MESSAGE
          ===================================================== */}

      {successMessage && (
        <div className="dashboard-success">
          <span>✓</span>
          <span>{successMessage}</span>
        </div>
      )}

      {/* =====================================================
          STATISTICS
          ===================================================== */}

      <section className="dashboard-stats">

        <div className="stat-card">
          <div className="stat-label">
            Total Logs
          </div>

          <div className="stat-value">
            {totalElements}
          </div>

          <div className="stat-description">
            Logs available in the system
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-label">
            Errors
          </div>

          <div className="stat-value">
            {errorCount}
          </div>

          <div className="stat-description">
            Errors on current page
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-label">
            Warnings
          </div>

          <div className="stat-value">
            {warningCount}
          </div>

          <div className="stat-description">
            Warnings on current page
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-label">
            AI Analyses
          </div>

          <div className="stat-value">
            {analyzedCount}
          </div>

          <div className="stat-description">
            Analyzed logs on current page
          </div>
        </div>

      </section>

      {/* =====================================================
          ANALYSIS BANNER
          ===================================================== */}

      <section className="analysis-banner">

        <div className="analysis-banner-content">
          <h3>
            Log Analysis
          </h3>

          <p>
            Ingest application logs and investigate
            anomalies using ML and AI.
          </p>
        </div>

        <button
          className="primary-button"
          onClick={() => navigate("/logs/ingest")}
        >
          <Upload size={15} />
          Ingest Log
        </button>

      </section>

      {/* =====================================================
          SEARCH
          ===================================================== */}

      <section className="search-panel">

        <div className="search-panel-header">

          <h2>
            Search &amp; Filter Logs
          </h2>

          <p>
            Narrow down logs by level, source,
            environment, application, or message.
          </p>

        </div>

        <div className="filter-grid">

          {/* Level */}

          <div className="filter-field">
            <label htmlFor="level">
              Level
            </label>

            <select
              id="level"
              name="level"
              value={filters.level}
              onChange={handleFilterChange}
            >
              <option value="">
                All levels
              </option>

              <option value="TRACE">
                TRACE
              </option>

              <option value="DEBUG">
                DEBUG
              </option>

              <option value="INFO">
                INFO
              </option>

              <option value="WARN">
                WARN
              </option>

              <option value="ERROR">
                ERROR
              </option>
            </select>
          </div>

          {/* Source */}

          <div className="filter-field">
            <label htmlFor="source">
              Source
            </label>

            <select
              id="source"
              name="source"
              value={filters.source}
              onChange={handleFilterChange}
            >
              <option value="">
                All sources
              </option>

              <option value="SPRING_BOOT">
                SPRING_BOOT
              </option>

              <option value="DOCKER">
                DOCKER
              </option>

              <option value="NGINX">
                NGINX
              </option>

              <option value="KUBERNETES">
                KUBERNETES
              </option>

              <option value="APACHE">
                APACHE
              </option>

              <option value="JENKINS">
                JENKINS
              </option>

              <option value="KAFKA">
                KAFKA
              </option>

              <option value="REDIS">
                REDIS
              </option>

              <option value="MYSQL">
                MYSQL
              </option>

              <option value="SYSTEM">
                SYSTEM
              </option>

              <option value="CUSTOM">
                CUSTOM
              </option>
            </select>
          </div>

          {/* Environment */}

          <div className="filter-field">
            <label htmlFor="environment">
              Environment
            </label>

            <select
              id="environment"
              name="environment"
              value={filters.environment}
              onChange={handleFilterChange}
            >
              <option value="">
                All environments
              </option>

              <option value="DEVELOPMENT">
                DEVELOPMENT
              </option>

              <option value="TESTING">
                TESTING
              </option>

              <option value="QA">
                QA
              </option>

              <option value="STAGING">
                STAGING
              </option>

              <option value="PRODUCTION">
                PRODUCTION
              </option>
            </select>
          </div>

          {/* Anomaly */}

          <div className="filter-field">
            <label htmlFor="anomaly">
              Anomaly
            </label>

            <select
              id="anomaly"
              name="anomaly"
              value={filters.anomaly}
              onChange={handleFilterChange}
            >
              <option value="">
                All logs
              </option>

              <option value="true">
                Anomalies only
              </option>

              <option value="false">
                Normal only
              </option>
            </select>
          </div>

          {/* Application */}

          <div className="filter-field">
            <label htmlFor="applicationName">
              Application
            </label>

            <input
              id="applicationName"
              type="text"
              name="applicationName"
              value={filters.applicationName}
              onChange={handleFilterChange}
              placeholder="payment-service"
            />
          </div>

          {/* Message */}

          <div className="filter-field">
            <label htmlFor="message">
              Message
            </label>

            <input
              id="message"
              type="text"
              name="message"
              value={filters.message}
              onChange={handleFilterChange}
              placeholder="Search message"
            />
          </div>

        </div>

        <div className="search-actions">

          <button
            className="primary-button"
            onClick={handleSearch}
          >
            <Search size={15} />
            Search Logs
          </button>

        </div>

      </section>

      {/* =====================================================
          ERROR
          ===================================================== */}

      {error && (
        <div className="dashboard-error">
          <CircleAlert size={15} />
          <span>{error}</span>
        </div>
      )}

      {/* =====================================================
    LOGS
    ===================================================== */}

      <section className="logs-section">

        <div className="logs-header">

          <div className="logs-title">

            <div>
              <h2>Log Explorer</h2>

              <p>
                Application events detected by DevInsight
              </p>
            </div>

            <span className="logs-count">
              {totalElements}{" "}
              {totalElements === 1 ? "result" : "results"}
            </span>

          </div>

        </div>

        {/* Loading */}

        {loading && (
          <div className="dashboard-message">
            Loading logs...
          </div>
        )}

        {/* Empty */}

        {!loading && logs.length === 0 && (
          <div className="dashboard-message">

            <FileText size={24} />

            <div style={{ marginTop: "8px" }}>
              No logs found.
            </div>

          </div>
        )}

        {/* Log cards */}

        {!loading &&
          logs.map((log) => {

            const levelClass =
              getLevelClass(log.level);

            const analysisClass =
              getAnalysisClass(
                log.analysisStatus
              );

            return (
              <article
                className="log-card"
                key={log.id}
              >

                <div className="log-card-main">

                  {/* Header */}

                  <div className="log-card-header">

                    <div className="log-identity">

                      <span
                        className={`log-level ${levelClass}`}
                      >
                        {log.level || "UNKNOWN"}
                      </span>

                      <span className="log-id">
                        #{log.id}
                      </span>

                      <span className="log-time">
                        {formatTimestamp(
                          log.timestamp
                        )}
                      </span>

                    </div>

                  </div>

                  {/* Metadata */}

                  <div className="log-meta">

                    <div className="log-meta-item">

                      <span className="log-meta-label">
                        Application
                      </span>

                      <span className="log-meta-value">
                        {log.applicationName ||
                          "Unknown"}
                      </span>

                    </div>

                    <div className="log-meta-item">

                      <span className="log-meta-label">
                        Source
                      </span>

                      <span className="log-meta-value">
                        {log.source || "Unknown"}
                      </span>

                    </div>

                    <div className="log-meta-item">

                      <span className="log-meta-label">
                        Environment
                      </span>

                      <span className="log-meta-value">
                        {log.environment ||
                          "Unknown"}
                      </span>

                    </div>

                    <div className="log-meta-item">

                      <span className="log-meta-label">
                        Prediction
                      </span>

                      <span className="log-meta-value">
                        {log.predictionLabel ||
                          "Not analyzed"}
                      </span>

                    </div>

                  </div>

                  {/* Message */}

                  <div className="log-message">

                    <span className="log-message-label">
                      MESSAGE
                    </span>

                    <span className="log-message-text">
                      {log.message ||
                        "No message available"}
                    </span>

                  </div>

                  {/* Analysis */}

                  <div className="log-analysis">

                    <span className="analysis-label">
                      ML
                    </span>

                    <span
                      className={`analysis-badge ${
                        log.predictionLabel ===
                        "Anomaly"
                          ? "anomaly"
                          : log.predictionLabel
                            ? "normal"
                            : "unknown"
                      }`}
                    >
                      {log.predictionLabel ||
                        "UNKNOWN"}
                    </span>

                    <span className="analysis-label">
                      Analysis
                    </span>

                    <span
                      className={`analysis-badge ${analysisClass}`}
                    >
                      {log.analysisStatus ||
                        "N/A"}
                    </span>

                  </div>

                </div>

                {/* Actions */}

                <div className="log-card-actions">

                  <button
                    className="secondary-button"
                    onClick={() =>
                      navigate(
                        `/logs/${log.id}/analyze`
                      )
                    }
                  >
                    <Brain size={13} />
                    Analyze
                  </button>

                  <button
                    className="danger-button"
                    onClick={() =>
                      handleDelete(log.id)
                    }
                  >
                    <Trash2 size={13} />
                    Delete
                  </button>

                </div>

              </article>
            );
          })}

      </section>

      {/* =====================================================
          PAGINATION
          ===================================================== */}

      {!loading && totalPages > 0 && (
        <section className="pagination">

          <button
            className="pagination-button"
            onClick={handlePrevious}
            disabled={page === 0}
          >
            <ChevronLeft size={14} />
            Previous
          </button>

          <span className="pagination-info">
            Page {page + 1} of {totalPages}
          </span>

          <button
            className="pagination-button"
            onClick={handleNext}
            disabled={
              page >= totalPages - 1
            }
          >
            Next
            <ChevronRight size={14} />
          </button>

        </section>
      )}

    </div>
  );
};

export default Dashboard;