import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AlertCircle,
  ArrowLeft,
  Brain,
  CheckCircle2,
  Cpu,
  FileSearch,
  Lightbulb,
  Loader2,
  ShieldAlert,
  Wrench,
} from "lucide-react";

import { analyzeLog, getLogById } from "../api/logApi";
import "./LogAnalysis.css";

const LogAnalysis = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [log, setLog] = useState(null);
  const [model, setModel] = useState("phi3:mini");

  const [analysisTypes, setAnalysisTypes] = useState([
    "SUMMARY",
    "ROOT_CAUSE",
    "SUGGESTED_FIX",
  ]);

  const [result, setResult] = useState(null);
  const [loadingLog, setLoadingLog] = useState(true);
  const [loadingAnalysis, setLoadingAnalysis] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadLog = async () => {
      try {
        setError("");

        const response = await getLogById(id);
        setLog(response);
      } catch (error) {
        console.error(error);

        setError(
          error.response?.data?.message ||
            "Failed to load log."
        );
      } finally {
        setLoadingLog(false);
      }
    };

    loadLog();
  }, [id]);

  const handleAnalysisTypeChange = (type) => {
    setAnalysisTypes((previous) =>
      previous.includes(type)
        ? previous.filter((item) => item !== type)
        : [...previous, type]
    );
  };

  const handleAnalyze = async () => {
    if (analysisTypes.length === 0) {
      setError("Select at least one analysis type.");
      return;
    }

    try {
      setError("");
      setResult(null);
      setLoadingAnalysis(true);

      const response = await analyzeLog(id, {
        model,
        analysisTypes,
      });

      console.log("Analysis result:", response);

      setResult(response);
    } catch (error) {
      console.error(error);

      setError(
        error.response?.data?.message ||
          "Failed to analyze log. Please try again."
      );
    } finally {
      setLoadingAnalysis(false);
    }
  };

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

  const prediction =
    log?.predictionLabel || "UNKNOWN";

  const predictionClass =
    prediction.toLowerCase() === "anomaly"
      ? "anomaly"
      : prediction.toLowerCase() === "normal"
        ? "normal"
        : "unknown";

  if (loadingLog) {
    return (
      <div className="analysis-loading">
        <Loader2 size={20} className="loading-spinner" />
        <span>Loading log...</span>
      </div>
    );
  }

  if (!log) {
    return (
      <div className="analysis-empty">
        <AlertCircle size={24} />

        <p>
          {error || "Log not found."}
        </p>

        <button
          className="primary-button"
          onClick={() => navigate("/dashboard")}
        >
          <ArrowLeft size={15} />
          Back to Dashboard
        </button>
      </div>
    );
  }

  return (
    <div className="log-analysis-page">

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

      <section className="analysis-header">

        <div className="analysis-header-left">

        <div className="analysis-title-row">

          <h2 className="analysis-title">
            Log #{id}
          </h2>

          <span
            className={`analysis-level ${getLevelClass(
              log.level
            )}`}
          >
            {log.level || "UNKNOWN"}
          </span>

        </div>

        <p className="analysis-subtitle">
          {log.applicationName || "Unknown application"}
          {" • "}
          {log.source || "Unknown source"}
          {" • "}
          {log.environment || "Unknown environment"}
        </p>

      </div>

      </section>

      {/* =========================================
          LOG DETAILS
          ========================================= */}

      <section className="log-detail-card">

        <div className="log-detail-header">
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <FileSearch size={16} />
            Log Details
          </div>
        </div>

        <div className="log-detail-body">

          <div className="log-detail-grid">

            <div className="detail-item">
              <span className="detail-label">
                Application
              </span>

              <span className="detail-value">
                {log.applicationName || "Unknown"}
              </span>
            </div>

            <div className="detail-item">
              <span className="detail-label">
                Source
              </span>

              <span className="detail-value">
                {log.source || "Unknown"}
              </span>
            </div>

            <div className="detail-item">
              <span className="detail-label">
                Environment
              </span>

              <span className="detail-value">
                {log.environment || "Unknown"}
              </span>
            </div>

            <div className="detail-item">
              <span className="detail-label">
                Timestamp
              </span>

              <span className="detail-value">
                {log.timestamp || "Unknown"}
              </span>
            </div>

          </div>

          <div className="log-message-box">

            <span className="detail-label">
              LOG MESSAGE
            </span>

            <pre>
              {log.message || "No message available"}
            </pre>

          </div>

        </div>

      </section>

      {/* =========================================
          ML + AI WORKSPACE
          ========================================= */}

      <section className="analysis-workspace">

        {/* AI CONFIGURATION */}

        <div className="analysis-config">

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <Brain size={17} />
            <h3 className="panel-title">
              AI Analysis
            </h3>
          </div>

          <p className="panel-description">
            Configure the AI analysis you want
            to perform on this log.
          </p>

          {/* Model */}

          <div className="model-field">

            <label htmlFor="model">
              LLM Model
            </label>

            <select
              id="model"
              value={model}
              onChange={(event) =>
                setModel(event.target.value)
              }
            >
              <option value="phi3:mini">
                phi3:mini
              </option>

              <option value="llama3">
                llama3
              </option>

              <option value="mistral">
                mistral
              </option>

              <option value="phi4">
                phi4
              </option>
            </select>

          </div>

          {/* Analysis Types */}

          <div className="analysis-options-title">
            Analysis Types
          </div>

          <label className="analysis-option">
            <input
              type="checkbox"
              checked={analysisTypes.includes(
                "SUMMARY"
              )}
              onChange={() =>
                handleAnalysisTypeChange(
                  "SUMMARY"
                )
              }
            />

            <span>Summary</span>
          </label>

          <label className="analysis-option">
            <input
              type="checkbox"
              checked={analysisTypes.includes(
                "ROOT_CAUSE"
              )}
              onChange={() =>
                handleAnalysisTypeChange(
                  "ROOT_CAUSE"
                )
              }
            />

            <span>Root Cause</span>
          </label>

          <label className="analysis-option">
            <input
              type="checkbox"
              checked={analysisTypes.includes(
                "SUGGESTED_FIX"
              )}
              onChange={() =>
                handleAnalysisTypeChange(
                  "SUGGESTED_FIX"
                )
              }
            />

            <span>Suggested Fix</span>
          </label>

          {error && (
            <div className="analysis-error">
              <AlertCircle size={15} />
              <span>{error}</span>
            </div>
          )}

          <button
            className="analysis-run-button"
            onClick={handleAnalyze}
            disabled={
              loadingAnalysis ||
              analysisTypes.length === 0
            }
          >
            {loadingAnalysis ? (
              <>
                <Loader2
                  size={15}
                  className="loading-spinner"
                />

                Analyzing...
              </>
            ) : (
              <>
                <Brain size={15} />
                Analyze Log
              </>
            )}
          </button>

        </div>

        {/* ML DETECTION */}

        <div className="ml-detection">

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <Cpu size={17} />

            <h3 className="panel-title">
              ML Detection
            </h3>
          </div>

          <p className="panel-description">
            Machine learning prediction for this log.
          </p>

          <div className="ml-detection-grid">

            <div className="ml-item">

              <span className="ml-item-label">
                Prediction
              </span>

              <span
                className={`ml-item-value ${predictionClass}`}
              >
                {prediction}
              </span>

            </div>

            <div className="ml-item">

              <span className="ml-item-label">
                Decision Score
              </span>

              <span className="ml-item-value">
                {log.decisionScore !== null &&
                log.decisionScore !== undefined
                  ? log.decisionScore
                  : "N/A"}
              </span>

            </div>

            <div className="ml-item">

              <span className="ml-item-label">
                Model Version
              </span>

              <span className="ml-item-value">
                {log.modelVersion || "N/A"}
              </span>

            </div>

            <div className="ml-item">

              <span className="ml-item-label">
                Analysis Status
              </span>

              <span className="ml-item-value">
                {log.analysisStatus || "N/A"}
              </span>

            </div>

          </div>

        </div>

      </section>

      {/* =========================================
          AI RESULT
          ========================================= */}

      {result && (
        <section className="ai-result-section">

          <div className="ai-result-header">

            <h2>
              AI Analysis Result
            </h2>

            <p>
              AI-generated investigation of Log #{id}
            </p>

          </div>

          <div className="ai-result-card">

            {/* Meta */}

            {(
                result.severity ||
                (result.confidenceScore !== null &&
                  result.confidenceScore !== undefined)
              ) && (
              <div className="ai-result-block">

                <div className="result-meta">

                  {result.severity && (
                    <div className="result-meta-item">
                      <ShieldAlert size={13} />

                      <span className="result-meta-label">
                        Severity
                      </span>

                      <span className="result-meta-value">
                        {result.severity}
                      </span>
                    </div>
                  )}

                  {result.confidenceScore !== null &&
                      result.confidenceScore !== undefined && (
                    <div className="result-meta-item">
                      <CheckCircle2 size={13} />

                      <span className="result-meta-label">
                        Confidence
                      </span>

                      <span className="result-meta-value">
                        {result.confidenceScore}
                      </span>
                    </div>
                  )}

                </div>

              </div>
            )}

            {/* Summary */}

            {result.summary && (
              <div className="ai-result-block">

                <h3>
                  <FileSearch
                    size={14}
                    style={{
                      marginRight: "7px",
                      verticalAlign: "middle",
                    }}
                  />

                  Summary
                </h3>

                <p>
                  {result.summary}
                </p>

              </div>
            )}

            {/* Root Cause */}

            {(result.probableRootCause ||
              result.rootCause) && (
              <div className="ai-result-block">

                <h3>
                  <AlertCircle
                    size={14}
                    style={{
                      marginRight: "7px",
                      verticalAlign: "middle",
                    }}
                  />

                  Probable Root Cause
                </h3>

                <p>
                  {result.probableRootCause ||
                    result.rootCause}
                </p>

              </div>
            )}

            {/* Suggested Fix */}

            {(result.suggestedFix ||
              result.recommendation) && (
              <div className="ai-result-block">

                <h3>
                  <Wrench
                    size={14}
                    style={{
                      marginRight: "7px",
                      verticalAlign: "middle",
                    }}
                  />

                  Suggested Fix
                </h3>

                <p>
                  {result.suggestedFix ||
                    result.recommendation}
                </p>

              </div>
            )}

            {/* Recommended Actions */}

            {result.recommendedActions &&
              result.recommendedActions.length > 0 && (
                <div className="ai-result-block">

                  <h3>
                    <Lightbulb
                      size={14}
                      style={{
                        marginRight: "7px",
                        verticalAlign: "middle",
                      }}
                    />

                    Recommended Actions
                  </h3>

                  <ol className="recommended-actions">

                    {result.recommendedActions.map(
                      (action, index) => (
                        <li key={index}>
                          {action}
                        </li>
                      )
                    )}

                  </ol>

                </div>
              )}

          </div>

        </section>
      )}

    </div>
  );
};

export default LogAnalysis;