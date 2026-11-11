import { useState } from "react";
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  Brain,
  Check,
  CheckCircle2,
  Clock,
  Copy,
  Cpu,
  Layers,
  Play,
  RotateCcw,
  ShieldAlert,
  Terminal,
  Wrench,
} from "lucide-react";

import { inspectLog } from "../api/logApi";
import "./LogInspector.css";

const SAMPLE_PRESETS = [
  {
    label: "Spring Boot (Port Conflict)",
    appType: "SPRING_BOOT",
    applicationName: "payment-service",
    log: `2026-06-26T20:15:31.123+05:30 ERROR 14201 --- [main] o.s.b.web.embedded.tomcat.TomcatWebServer : Port 8080 was already in use.
org.springframework.boot.web.server.PortInUseException: Port 8080 is already in use
\tat org.springframework.boot.web.embedded.tomcat.TomcatWebServer.start(TomcatWebServer.java:242)
\tat org.springframework.boot.SpringApplication.startWebServer(SpringApplication.java:312)
\tat org.springframework.boot.SpringApplication.run(SpringApplication.java:1305)`,
  },
  {
    label: "Docker (Redis Unreachable)",
    appType: "DOCKER",
    applicationName: "worker-container",
    log: `2026-06-26T20:15:31.123456789Z Traceback (most recent call last):
  File "worker.py", line 45, in connect
    r = redis.Redis(host='redis-cache', port=6379, socket_timeout=3)
  File "redis/client.py", line 287, in execute_command
    return self.parse_response(conn, command_name, **options)
ConnectionError: Error 111 connecting to redis-cache:6379. Connection refused.`,
  },
  {
    label: "Kubernetes (CrashLoopBackOff)",
    appType: "KUBERNETES",
    applicationName: "auth-service",
    log: `2026-06-26T20:15:31.123456789Z stderr F [FATAL] Failed to initialize PostgreSQL datasource at jdbc:postgresql://postgres-db:5432/auth
2026-06-26T20:15:31.123456789Z stderr F org.postgresql.util.PSQLException: Connection to postgres-db:5432 refused.
2026-06-26T20:15:31.123456789Z stderr F Container terminated with exit code 1. Status: CrashLoopBackOff.`,
  },
  {
    label: "Clean INFO (Healthy Ping)",
    appType: "SPRING_BOOT",
    applicationName: "inventory-service",
    log: `2026-06-26T20:15:31.123+05:30 INFO 9821 --- [scheduling-1] c.p.service.HealthJob : Inventory synchronization heartbeat completed in 14ms.`,
  },
];

const LogInspector = () => {
  const [appType, setAppType] = useState("SPRING_BOOT");
  const [analysisMode, setAnalysisMode] = useState("AUTO");
  const [environment, setEnvironment] = useState("PRODUCTION");
  const [applicationName, setApplicationName] = useState("");
  const [logText, setLogText] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [copiedIndex, setCopiedIndex] = useState(null);

  const handleApplyPreset = (preset) => {
    setAppType(preset.appType);
    setApplicationName(preset.applicationName);
    setLogText(preset.log);
    setResult(null);
    setError("");
  };

  const handleClear = () => {
    setLogText("");
    setResult(null);
    setError("");
  };

  const handleInspect = async (event) => {
    event.preventDefault();

    if (!logText.trim()) {
      setError("Please paste a log or stack trace before inspecting.");
      return;
    }

    setLoading(true);
    setError("");
    setResult(null);

    try {
      const response = await inspectLog({
        log: logText,
        appType,
        environment,
        applicationName: applicationName.trim() || undefined,
        analysisMode,
      });

      setResult(response);
    } catch (err) {
      console.error(err);
      setError(
        err.response?.data?.message ||
          "Failed to inspect log. Check your Gateway and Log Layer connection."
      );
    } finally {
      setLoading(false);
    }
  };

  const handleCopyCommand = (command, index) => {
    navigator.clipboard.writeText(command);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  return (
    <div className="inspector-page">
      {/* Header */}
      <div className="inspector-header">
        <div>
          <div className="inspector-title">
            <Terminal size={26} className="text-primary" />
            Live Log Inspector & Incident Sandbox
          </div>
          <div className="inspector-subtitle">
            Enterprise unified inspection engine combining resilient multi-line parsing, ML Isolation Forest anomaly detection, and selective RAG LLM incident diagnosis.
          </div>
        </div>
      </div>

      {/* Preset Quick Fill Buttons */}
      <div className="presets-toolbar">
        <span className="presets-label">Quick Samples:</span>
        {SAMPLE_PRESETS.map((preset, index) => (
          <button
            key={index}
            type="button"
            className="preset-btn"
            onClick={() => handleApplyPreset(preset)}
          >
            {preset.label}
          </button>
        ))}
      </div>

      {/* Input Card */}
      <div className="inspector-card">
        <form onSubmit={handleInspect}>
          <div className="inspector-controls-grid">
            <div className="form-group">
              <label>Application / Log Type</label>
              <select
                value={appType}
                onChange={(e) => setAppType(e.target.value)}
              >
                <option value="SPRING_BOOT">Spring Boot</option>
                <option value="DOCKER">Docker Container</option>
                <option value="KUBERNETES">Kubernetes Pod</option>
                <option value="NGINX">Nginx Web Server</option>
                <option value="CUSTOM">Custom / Generic</option>
              </select>
            </div>

            <div className="form-group">
              <label>Analysis Mode</label>
              <select
                value={analysisMode}
                onChange={(e) => setAnalysisMode(e.target.value)}
              >
                <option value="AUTO">AUTO (ML + Deep LLM on Anomaly)</option>
                <option value="FAST">FAST (ML & Rules only, &lt;15ms)</option>
                <option value="FORCE_DEEP">FORCE DEEP (Always run RAG LLM)</option>
              </select>
            </div>

            <div className="form-group">
              <label>Environment</label>
              <select
                value={environment}
                onChange={(e) => setEnvironment(e.target.value)}
              >
                <option value="PRODUCTION">Production</option>
                <option value="STAGING">Staging</option>
                <option value="DEVELOPMENT">Development</option>
              </select>
            </div>

            <div className="form-group">
              <label>Application Name</label>
              <input
                type="text"
                placeholder="e.g. payment-service"
                value={applicationName}
                onChange={(e) => setApplicationName(e.target.value)}
              />
            </div>
          </div>

          <div className="log-textarea-container">
            <label>
              <span>Raw Log or Multi-Line Stack Trace</span>
              {logText && (
                <button
                  type="button"
                  className="clear-text-btn"
                  onClick={handleClear}
                >
                  Clear log
                </button>
              )}
            </label>
            <textarea
              className="log-textarea"
              placeholder="Paste single line log or full stack trace here..."
              value={logText}
              onChange={(e) => setLogText(e.target.value)}
              rows={8}
            />
          </div>

          <div className="inspector-actions">
            <button
              type="submit"
              className="btn-inspect"
              disabled={loading || !logText.trim()}
            >
              {loading ? (
                <>
                  <Activity size={16} className="animate-spin" />
                  Analyzing Log...
                </>
              ) : (
                <>
                  <Play size={16} />
                  Run Industrial Inspection
                </>
              )}
            </button>
          </div>
        </form>
      </div>

      {/* Error Alert */}
      {error && (
        <div className="ingestion-error" style={{ marginBottom: "20px" }}>
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Results Section */}
      {result && (
        <div className="results-container">
          {/* Top Metrics Row */}
          <div className="metrics-summary-grid">
            {/* Anomaly Badge */}
            <div className="metric-card">
              <div className="metric-card-header">
                <span>Anomaly Detection</span>
                <Cpu size={16} />
              </div>
              <div className="metric-value-row">
                <span
                  className={`status-badge ${
                    result.hasAnomaly ? "anomaly" : "normal"
                  }`}
                >
                  {result.hasAnomaly ? (
                    <>
                      <ShieldAlert size={15} />
                      ANOMALY DETECTED
                    </>
                  ) : (
                    <>
                      <CheckCircle2 size={15} />
                      NORMAL
                    </>
                  )}
                </span>
              </div>
              {result.detectionMetrics?.decisionScore !== undefined && (
                <div style={{ fontSize: "12px", color: "var(--text-secondary)", marginTop: "8px" }}>
                  Decision Score: <strong>{result.detectionMetrics.decisionScore.toFixed(4)}</strong>
                </div>
              )}
            </div>

            {/* Severity */}
            <div className="metric-card">
              <div className="metric-card-header">
                <span>Parsed Severity</span>
                <Layers size={16} />
              </div>
              <div className="metric-value-row">
                <span className={`level-badge ${result.level}`}>
                  {result.level || "UNKNOWN"}
                </span>
                {result.hasStackTrace && (
                  <span className="heuristic-tag" style={{ color: "#dc2626", borderColor: "#fecaca" }}>
                    Stack Trace Present
                  </span>
                )}
              </div>
            </div>

            {/* Performance Time */}
            <div className="metric-card">
              <div className="metric-card-header">
                <span>Processing Latency</span>
                <Clock size={16} />
              </div>
              <div className="metric-value-row" style={{ fontSize: "20px", fontWeight: "700" }}>
                {result.executionTimeMs} ms
              </div>
              <div style={{ fontSize: "12px", color: "var(--text-secondary)", marginTop: "4px" }}>
                Source: {result.appType}
              </div>
            </div>

            {/* Detection Heuristic Flags */}
            <div className="metric-card">
              <div className="metric-card-header">
                <span>Heuristic Rules</span>
                <Activity size={16} />
              </div>
              <div className="flags-container">
                {result.detectionMetrics?.heuristicFlags?.length > 0 ? (
                  result.detectionMetrics.heuristicFlags.map((flag, idx) => (
                    <span key={idx} className="heuristic-tag">
                      {flag}
                    </span>
                  ))
                ) : (
                  <span style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                    No rule violations
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* AI Incident Diagnosis Card */}
          {result.incidentDiagnosis ? (
            <div
              className={`diagnosis-card ${
                result.incidentDiagnosis.severity === "CRITICAL" ? "critical" : ""
              }`}
            >
              <div className="diagnosis-title-row">
                <div className="diagnosis-title">
                  <Brain size={20} className="text-primary" />
                  AI-Powered Root Cause Diagnosis
                </div>
                <span className={`level-badge ${result.incidentDiagnosis.severity}`}>
                  {result.incidentDiagnosis.severity}
                </span>
              </div>

              {/* Summary */}
              <div className="diagnosis-section">
                <div className="diagnosis-section-title">Incident Summary</div>
                <div className="diagnosis-text">
                  {result.incidentDiagnosis.summary}
                </div>
              </div>

              {/* Root Cause */}
              <div className="diagnosis-section">
                <div className="diagnosis-section-title">Probable Root Cause</div>
                <div className="diagnosis-text">
                  {result.incidentDiagnosis.probableRootCause}
                </div>
              </div>

              {/* Suggested Fix */}
              <div className="diagnosis-section">
                <div className="diagnosis-section-title">Recommended Remediation</div>
                <div className="diagnosis-text">
                  {result.incidentDiagnosis.suggestedFix}
                </div>
              </div>

              {/* Copyable CLI Commands */}
              {result.incidentDiagnosis.remediationCommands?.length > 0 && (
                <div className="diagnosis-section">
                  <div className="diagnosis-section-title" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                    <Wrench size={14} />
                    Remediation Commands
                  </div>
                  <div className="remediation-box">
                    {result.incidentDiagnosis.remediationCommands.map((cmd, idx) => (
                      <div key={idx} className="command-row">
                        <span className="command-code">$ {cmd}</span>
                        <button
                          type="button"
                          className="btn-copy-cmd"
                          onClick={() => handleCopyCommand(cmd, idx)}
                        >
                          {copiedIndex === idx ? (
                            <>
                              <Check size={12} color="#16a34a" />
                              Copied
                            </>
                          ) : (
                            <>
                              <Copy size={12} />
                              Copy
                            </>
                          )}
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="diagnosis-card" style={{ borderLeftColor: "var(--success)" }}>
              <div className="diagnosis-title" style={{ color: "var(--success)" }}>
                <CheckCircle2 size={20} />
                No Deep Incident Analysis Triggered
              </div>
              <div className="diagnosis-text" style={{ marginTop: "10px" }}>
                The log was classified as normal and non-critical. Fast-path execution completed without triggering compute-intensive RAG inference.
                To force deep root-cause analysis on normal logs, change the <strong>Analysis Mode</strong> to <strong>FORCE DEEP</strong>.
              </div>
            </div>
          )}

          {/* Structured Log Preview */}
          <div className="inspector-card">
            <h4 style={{ fontSize: "14px", fontWeight: "600", marginBottom: "12px", color: "var(--text-primary)" }}>
              Structured Log Payload
            </h4>
            <div className="log-preview-box">
              {JSON.stringify(
                {
                  correlationId: result.correlationId,
                  appType: result.appType,
                  applicationName: result.applicationName,
                  environment: result.environment,
                  timestamp: result.timestamp,
                  level: result.level,
                  message: result.message,
                  metadata: result.metadata,
                },
                null,
                2
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default LogInspector;

