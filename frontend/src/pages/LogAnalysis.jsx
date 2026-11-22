import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
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
import { getLogSource } from "../api/observabilityApi";
import { useWorkspace } from "../context/workspace";
import useResource, { readError } from "../hooks/useResource";
import { label } from "../observability";
import "./LogAnalysis.css";

function LogAnalysisDetail({ id }) {
  const navigate = useNavigate();
  const { organization } = useWorkspace();
  const org = organization.id;
  const [showSource, setShowSource] = useState(false);
  const loadSource = useCallback((signal) => getLogSource(org, id, signal), [org, id]);
  const source = useResource(showSource && `${org}/source/${id}`, loadSource);

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
    const controller = new AbortController();
    const loadLog = async () => {
      try {
        setError("");

        const response = await getLogById(id, org, controller.signal);
        if (!controller.signal.aborted) setLog(response);
      } catch (error) {
        if (!controller.signal.aborted) setError(readError(error));
      } finally {
        if (!controller.signal.aborted) setLoadingLog(false);
      }
    };

    loadLog();
    return () => controller.abort();
  }, [id, org]);

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

      setResult(response);
    } catch (error) {
      setError(readError(error));
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
    (log?.analysisStatus === "COMPLETED" && log?.predictionLabel) || "Unavailable";

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
          onClick={() => navigate("/logs")}
        >
          <ArrowLeft size={15} />
          Back to logs
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
        onClick={() => navigate("/logs")}
      >
        <ArrowLeft size={15} />
        Back to logs
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

            <details className="work-details" onToggle={(event) => { if (event.currentTarget.open) setShowSource(true); }}>
              <summary>Original event</summary>
              {source.error && <p role="alert">{source.error}</p>}
              {source.data ? <pre>{source.data.raw || "No original message available"}</pre>
                : !source.error && <p role="status">Loading source event…</p>}
              {source.data?.incidentId && <Link className="work-link" to={`/incidents?incident=${source.data.incidentId}`}>View associated incident</Link>}
            </details>
            <dl className="work-properties">
              <div><dt>Environment</dt><dd>{label(log.environment)}</dd></div>
              <div><dt>Stored persistence time</dt><dd>{log.createdAt?.replace("T", " ") || "—"}</dd></div>
              <div><dt>Logger</dt><dd>{log.loggerName || "—"}</dd></div>
              <div><dt>Host</dt><dd>{log.hostName || "—"}</dd></div>
              <div><dt>Correlation ID</dt><dd>{log.correlationId ? <Link className="work-link" to={`/logs?correlation=${log.correlationId}&range=MONTH`}>{log.correlationId}</Link> : "—"}</dd></div>
              <div><dt>Reported trace context</dt><dd>{log.traceparent ? <Link className="work-link" to={`/logs?trace=${log.traceparent.slice(3,35)}&range=MONTH`}>{log.traceparent}</Link> : "—"}</dd></div>
              <div><dt>Trace provenance</dt><dd>{log.traceOrigin ? label(log.traceOrigin) : "—"}</dd></div>
              <div><dt>Diagnosis status</dt><dd>{label(log.llmAnalysisStatus)}</dd></div>
            </dl>
            {log.llmAnalysisStatus === "COMPLETED" && log.summary && <p className="work-prose">{log.summary}</p>}
            {log.llmAnalysisStatus === "COMPLETED" && log.suggestedFix && <pre>{log.suggestedFix}</pre>}

          </div>

        </div>

      </section>

      {/* =========================================
          ML + AI WORKSPACE
          ========================================= */}

      <section className="analysis-workspace">

        {/* AI CONFIGURATION */}

        {organization.role !== "VIEWER" && <div className="analysis-config">

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

        </div>}

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
                ML scoring status
              </span>

              <span className="ml-item-value">
                {log.analysisStatus || "N/A"}
              </span>

            </div>
            <div className="ml-item">
              <span className="ml-item-label">Operational triage</span>
              <span className="ml-item-value">{log.operationalRuleId ? `Critical · ${log.level}` : "No rule matched"}</span>
            </div>
            <div className="ml-item">
              <span className="ml-item-label">Diagnosis status</span>
              <span className="ml-item-value">{log.llmAnalysisStatus || (log.diagnosisEligible ? "Eligible" : "Not scheduled")}</span>
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

export default function LogAnalysis() {
  const { id } = useParams();
  return <LogAnalysisDetail key={id} id={id} />;
}
