import React, { useState, useEffect } from "react";
import {
  Layers,
  Copy,
  Check,
  Send,
  Trash2,
  AlertTriangle,
  CheckCircle,
  ExternalLink,
  Code2,
  Terminal,
  Server,
  Zap,
  RefreshCw,
  Eye,
  EyeOff,
  ShieldCheck,
  Activity,
  CheckCheck,
  KeyRound,
  Sparkles,
} from "lucide-react";
import {
  getIntegrations,
  createIntegration,
  deleteIntegration,
  runTestSimulation,
} from "../api/integrationApi";
import "./Integrations.css";

const TECH_STACKS = [
  {
    id: "SPRING_BOOT",
    name: "Java / Spring Boot",
    icon: "☕",
    color: "#10b981",
    badge: "Logback Appender",
    desc: "Asynchronous drop-in XML appender for Logback & Log4j2 with zero code modification.",
  },
  {
    id: "PYTHON",
    name: "Python (FastAPI / Django)",
    icon: "🐍",
    color: "#38bdf8",
    badge: "Logging Handler",
    desc: "Non-blocking logging.Handler and ASGI exception middleware for FastAPI, Django, and Flask.",
  },
  {
    id: "NODEJS",
    name: "Node.js (Express / Nest)",
    icon: "🟩",
    color: "#22c55e",
    badge: "Winston Transport",
    desc: "Custom Winston and Pino transports streaming unhandled errors and v8 stack traces.",
  },
  {
    id: "GO",
    name: "Go (Zap / Slog / Logrus)",
    icon: "🔵",
    color: "#06b6d4",
    badge: "HTTP Hook",
    desc: "Buffered HTTP hook for Go microservices capturing panics and goroutine traces.",
  },
  {
    id: "KUBERNETES",
    name: "Kubernetes & Docker",
    icon: "☸️",
    color: "#6366f1",
    badge: "Fluent Bit DaemonSet",
    desc: "Scrapes stdout/stderr across pods and namespaces without touching application code.",
  },
  {
    id: "POSTGRESQL",
    name: "Database / Infra",
    icon: "🐘",
    color: "#818cf8",
    badge: "Syslog / Error Logs",
    desc: "Captures database connection saturation, lock timeouts, and pool depletion logs.",
  },
];

const Integrations = () => {
  const [selectedStack, setSelectedStack] = useState("PYTHON");
  const [appName, setAppName] = useState("checkout-service");
  const [environment, setEnvironment] = useState("PRODUCTION");
  const [slackWebhookUrl, setSlackWebhookUrl] = useState("");
  const [slackChannelName, setSlackChannelName] = useState("#python-test");
  const [alertOnAnomaly, setAlertOnAnomaly] = useState(true);
  const [alertOnError, setAlertOnError] = useState(true);

  const [integrations, setIntegrations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const [revealedKeys, setRevealedKeys] = useState({});
  const [copiedKeyId, setCopiedKeyId] = useState(null);

  useEffect(() => {
    fetchIntegrations();
  }, []);

  const fetchIntegrations = async () => {
    try {
      setLoading(true);
      const data = await getIntegrations();
      setIntegrations(data);
    } catch (err) {
      console.error("Failed to fetch integrations", err);
    } finally {
      setLoading(false);
    }
  };

  const toggleKeyVisibility = (id) => {
    setRevealedKeys((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const copyApiKey = (id, key) => {
    navigator.clipboard.writeText(key);
    setCopiedKeyId(id);
    setTimeout(() => setCopiedKeyId(null), 2000);
  };

  const handleRegister = async (e) => {
    if (e && e.preventDefault) e.preventDefault();
    if (!appName.trim()) {
      setErrorMessage("Application name is required");
      return;
    }

    try {
      setSaving(true);
      setErrorMessage("");
      setSuccessMessage("");

      await createIntegration({
        applicationName: appName.trim(),
        techStack: selectedStack,
        environment: environment,
        slackWebhookUrl: slackWebhookUrl.trim(),
        slackChannelName: slackChannelName.trim(),
        alertOnAnomaly: alertOnAnomaly,
        alertOnError: alertOnError,
        description: `Integration for ${appName} (${selectedStack})`,
      });

      setSuccessMessage(`Integration for '${appName}' configured and registered successfully!`);
      await fetchIntegrations();
    } catch (err) {
      if (err.response?.status === 401) {
        setErrorMessage("Your session has expired. Please log out (top-right) and log in again.");
      } else {
        setErrorMessage(err.response?.data?.message || "Failed to register integration.");
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Are you sure you want to delete this integration?")) return;
    try {
      await deleteIntegration(id);
      await fetchIntegrations();
    } catch (err) {
      console.error("Failed to delete integration", err);
    }
  };

  const handleRunTest = async (targetAppName) => {
    const target = (targetAppName || appName).trim();
    if (!target) {
      setErrorMessage("Application name is required to run simulation.");
      return;
    }

    try {
      setTesting(true);
      setTestResult(null);
      setErrorMessage("");

      const isAlreadyRegistered = integrations.some((i) => i.applicationName === target);
      if (!isAlreadyRegistered) {
        setSuccessMessage(`Auto-registering '${target}' (${selectedStack}) before running simulation...`);
        try {
          await createIntegration({
            applicationName: target,
            techStack: selectedStack,
            environment: environment,
            slackWebhookUrl: slackWebhookUrl.trim(),
            slackChannelName: slackChannelName.trim(),
            alertOnAnomaly: alertOnAnomaly,
            alertOnError: alertOnError,
            description: `Auto-registered integration for ${target}`,
          });
          await fetchIntegrations();
        } catch (regErr) {
          if (regErr.response?.status === 401) {
            setErrorMessage("Your session has expired. Please log out (top-right) and log in again.");
            return;
          }
          console.warn("Auto-registration warning:", regErr);
        }
      }

      const result = await runTestSimulation({
        applicationName: target,
      });

      if (!result.success) {
        setErrorMessage(result.message || "Test simulation failed during ingest.");
      } else {
        setTestResult(result);
        setSuccessMessage(`Test simulation complete! Telemetry ingested for '${target}'.`);
      }
    } catch (err) {
      if (err.response?.status === 401) {
        setErrorMessage("Your session has expired. Please log out (top-right) and log in again.");
      } else {
        setErrorMessage(err.response?.data?.message || "Test simulation failed.");
      }
    } finally {
      setTesting(false);
    }
  };

  const generateSnippet = () => {
    const activeInt = integrations.find((i) => i.applicationName === appName);
    const apiKey = activeInt ? activeInt.apiKey : "<application-api-key>";
    const gatewayUrl = "http://localhost:8080";

    switch (selectedStack) {
      case "SPRING_BOOT":
        return `<!-- 1. Add to src/main/resources/logback-spring.xml -->
<configuration>
    <appender name="DEVINSIGHT" class="com.project.client.DevInsightLogbackAppender">
        <gatewayUrl>${gatewayUrl}</gatewayUrl>
        <apiKey>${apiKey}</apiKey>
        <applicationName>${appName}</applicationName>
        <environment>${environment}</environment>
    </appender>

    <root level="INFO">
        <appender-ref ref="DEVINSIGHT" />
    </root>
</configuration>`;

      case "PYTHON":
        return `# 1. Install & configure DevInsight Python Handler
import logging
from devinsight_logger import DevInsightHandler

logger = logging.getLogger("${appName}")
logger.setLevel(logging.INFO)

devinsight_handler = DevInsightHandler(
    gateway_url="${gatewayUrl}",
    api_key="${apiKey}",
    application_name="${appName}",
    environment="${environment}"
)
logger.addHandler(devinsight_handler)

# 2. Real-time streaming is active:
logger.error("Critical failure executing checkout payment transaction!")`;

      case "NODEJS":
        return `// 1. Configure Winston DevInsight Transport
const winston = require('winston');
const DevInsightTransport = require('./devinsight-transport');

const logger = winston.createLogger({
  transports: [
    new winston.transports.Console(),
    new DevInsightTransport({
      gatewayUrl: '${gatewayUrl}',
      apiKey: '${apiKey}',
      applicationName: '${appName}',
      environment: '${environment}'
    })
  ]
});

logger.error(new Error('Fatal connection pool timeout in ${appName}'));`;

      case "GO":
        return `// Go (Zap / Slog / Logrus Hook)
hook := devinsight.NewHook(&devinsight.Config{
    GatewayURL:      "${gatewayUrl}",
    APIKey:          "${apiKey}",
    ApplicationName: "${appName}",
    Environment:     "${environment}",
})
log.AddHook(hook)
log.Errorf("panic: runtime connection refused")`;

      case "KUBERNETES":
        return `# Fluent Bit Output Filter for ${appName}
[OUTPUT]
    Name        http
    Match       kube.*${appName}*
    Host        api-gateway.devinsight.svc.cluster.local
    Port        8080
    URI         /api/v1/logs
    Header      Content-Type application/json
    Header      X-API-Key ${apiKey}
    Format      json`;

      default:
        return `# cURL Direct Telemetry Post
curl -X POST ${gatewayUrl}/api/v1/logs \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: ${apiKey}" \\
  -d '{
    "rawLog": "ERROR [main] Fatal system failure in ${appName}",
    "source": "CUSTOM",
    "environment": "${environment}",
    "applicationName": "${appName}"
  }'`;
    }
  };

  const copyCode = () => {
    navigator.clipboard.writeText(generateSnippet());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getStackLanguage = () => {
    switch (selectedStack) {
      case "SPRING_BOOT": return "logback.xml";
      case "PYTHON": return "python";
      case "NODEJS": return "javascript";
      case "GO": return "go";
      case "KUBERNETES": return "yaml";
      default: return "bash";
    }
  };

  return (
    <div className="integrations-page">
      {/* Header */}
      <div className="integrations-header">
        <div className="header-badge">
          <Sparkles size={13} className="text-indigo" />
          <span>ENTERPRISE INTEGRATIONS & TELEMETRY HUB</span>
        </div>
        <h1>Multi-Tech-Stack Application Integrations</h1>
        <p>
          Connect any microservice or container to DevInsight in under 60 seconds.
          Select your tech stack, configure your dedicated Slack alert channel, and verify live ingestion with automated AI incident diagnosis.
        </p>
      </div>

      {/* Alert Messages */}
      {errorMessage && (
        <div className="alert-banner alert-error">
          <AlertTriangle size={18} />
          <span>{errorMessage}</span>
        </div>
      )}
      {successMessage && (
        <div className="alert-banner alert-success">
          <CheckCircle size={18} />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Step 1: Tech Stack Selector */}
      <div className="section-title">
        <span className="step-num">1</span>
        <h2>Select Application Technology Stack</h2>
      </div>

      <div className="tech-stack-grid">
        {TECH_STACKS.map((stack) => {
          const isActive = selectedStack === stack.id;
          return (
            <div
              key={stack.id}
              className={`tech-card ${isActive ? "active" : ""}`}
              onClick={() => setSelectedStack(stack.id)}
              style={{
                "--stack-accent": stack.color,
              }}
            >
              {isActive && (
                <div className="active-glow-pill">
                  <Check size={12} />
                  <span>SELECTED</span>
                </div>
              )}
              <div className="tech-card-header">
                <span className="tech-icon">{stack.icon}</span>
                <span className="tech-badge">{stack.badge}</span>
              </div>
              <h3>{stack.name}</h3>
              <p>{stack.desc}</p>
            </div>
          );
        })}
      </div>

      {/* Step 2: Configuration & Code Snippet Grid */}
      <div className="config-grid">
        {/* Left: Configuration Form */}
        <div className="config-card">
          <div className="card-header">
            <div className="card-header-icon">
              <Server size={18} />
            </div>
            <div>
              <h3>Service & Slack Alert Routing</h3>
              <span className="card-header-sub">Configure destination channel & ingestion credentials</span>
            </div>
          </div>

          <form onSubmit={handleRegister} className="integration-form">
            <div className="form-group">
              <label>Application / Service Name *</label>
              <input
                type="text"
                placeholder="e.g. checkout-service"
                value={appName}
                onChange={(e) => setAppName(e.target.value)}
                required
              />
              <span className="form-hint">Unique identifier used for tagging telemetry and routing Slack alerts.</span>
            </div>

            <div className="form-group">
              <label>Deployment Environment</label>
              <select value={environment} onChange={(e) => setEnvironment(e.target.value)}>
                <option value="PRODUCTION">PRODUCTION (Strict Alerting)</option>
                <option value="STAGING">STAGING</option>
                <option value="QA">QA</option>
                <option value="DEVELOPMENT">DEVELOPMENT</option>
              </select>
            </div>

            <div className="form-group">
              <label>Dedicated Slack Webhook URL</label>
              <input
                type="url"
                placeholder="https://hooks.slack.com/services/..."
                value={slackWebhookUrl}
                onChange={(e) => setSlackWebhookUrl(e.target.value)}
              />
              <span className="form-hint">Route incident alerts for this service directly to your team's Slack channel.</span>
            </div>

            <div className="form-group">
              <label>Slack Channel Tag</label>
              <input
                type="text"
                placeholder="#alerts-prod"
                value={slackChannelName}
                onChange={(e) => setSlackChannelName(e.target.value)}
              />
            </div>

            <div className="checkbox-row">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={alertOnAnomaly}
                  onChange={(e) => setAlertOnAnomaly(e.target.checked)}
                />
                <span>Alert on ML Anomaly</span>
              </label>

              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={alertOnError}
                  onChange={(e) => setAlertOnError(e.target.checked)}
                />
                <span>Alert on ERROR / FATAL</span>
              </label>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary" disabled={saving}>
                <Layers size={16} />
                <span>{saving ? "Saving Integration..." : "Register / Update Integration"}</span>
              </button>

              <button
                type="button"
                className="btn-secondary"
                onClick={() => handleRunTest(appName)}
                disabled={testing}
              >
                <Zap size={16} className={testing ? "spin" : "text-amber"} />
                <span>{testing ? "Testing Pipeline..." : "Send Test Simulation"}</span>
              </button>
            </div>
          </form>
        </div>

        {/* Right: Code Generator */}
        <div className="snippet-card">
          <div className="terminal-header">
            <div className="terminal-controls">
              <span className="control-dot dot-red"></span>
              <span className="control-dot dot-yellow"></span>
              <span className="control-dot dot-green"></span>
            </div>
            <div className="terminal-title">
              <Code2 size={14} />
              <span>{getStackLanguage()}</span>
            </div>
            <button className="copy-btn" onClick={copyCode} title="Copy Snippet">
              {copied ? <Check size={14} className="text-green" /> : <Copy size={14} />}
              <span>{copied ? "Copied!" : "Copy Code"}</span>
            </button>
          </div>

          <pre className="code-viewer">
            <code>{generateSnippet()}</code>
          </pre>

          <div className="snippet-footer">
            <ShieldCheck size={14} className="text-green" />
            <span className="footer-tip">
              Pre-configured with zero-loss async queue, headless gateway routing, and isolated worker threads.
            </span>
          </div>
        </div>
      </div>

      {/* Step 3: Test Simulation Results */}
      {testResult && (
        <div className="test-result-card">
          <div className="test-result-header">
            <div className={`result-badge ${testResult.success ? "success" : "failed"}`}>
              {testResult.success ? <CheckCircle size={18} /> : <AlertTriangle size={18} />}
              <span>{testResult.success ? "TEST SIMULATION SUCCESSFUL" : "TEST SIMULATION FAILED"}</span>
            </div>
            <span className="duration-tag">{testResult.durationMs} ms latency</span>
          </div>

          <div className="pipeline-stepper">
            <div className="step-item done">
              <div className="step-icon-wrap">
                <span className="step-dot">✓</span>
              </div>
              <div className="step-content">
                <span className="step-title">1. Ingested</span>
                <span className="step-detail">201 Created (Log #{testResult.logId})</span>
              </div>
            </div>

            <div className="step-divider"></div>

            <div className="step-item done">
              <div className="step-icon-wrap">
                <span className="step-dot">✓</span>
              </div>
              <div className="step-content">
                <span className="step-title">2. Isolation Forest ML</span>
                <span className="step-detail">
                  {testResult.anomalyDetected ? "Anomaly Detected" : "Normal"} ({testResult.anomalyScore?.toFixed(4)})
                </span>
              </div>
            </div>

            <div className="step-divider"></div>

            <div className="step-item done">
              <div className="step-icon-wrap">
                <span className="step-dot">✓</span>
              </div>
              <div className="step-content">
                <span className="step-title">3. RAG + Ollama AI</span>
                <span className="step-detail">Root-Cause Diagnosed</span>
              </div>
            </div>

            <div className="step-divider"></div>

            <div className="step-item done">
              <div className="step-icon-wrap">
                <span className="step-dot">{testResult.slackDispatched ? "✓" : "ℹ"}</span>
              </div>
              <div className="step-content">
                <span className="step-title">4. Slack Dispatched</span>
                <span className="step-detail">
                  {testResult.slackDispatched
                    ? `Delivered to ${testResult.slackChannel}`
                    : `Queued for ${testResult.slackChannel || "default channel"}`}
                </span>
              </div>
            </div>
          </div>

          <div className="test-result-details">
            <div className="detail-item">
              <span className="detail-label">Service:</span>
              <span className="detail-val">{testResult.applicationName}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Tech Stack:</span>
              <span className="detail-val">{testResult.techStack}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Diagnosis:</span>
              <span className="detail-val">{testResult.diagnosisSummary}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">Suggested Fix:</span>
              <code className="remediation-box">{testResult.remediation}</code>
            </div>
          </div>
        </div>
      )}

      {/* Step 4: Active Registered Integrations Table */}
      <div className="registered-section">
        <div className="section-header">
          <div>
            <h2>Active Registered Services ({integrations.length})</h2>
            <span className="section-subtitle">Manage integrated applications, live API keys, and notification routing</span>
          </div>
          <button className="refresh-btn" onClick={fetchIntegrations} disabled={loading}>
            <RefreshCw size={14} className={loading ? "spin" : ""} />
            <span>Refresh</span>
          </button>
        </div>

        {integrations.length === 0 ? (
          <div className="empty-state">
            <Terminal size={36} />
            <h3>No External Services Registered</h3>
            <p>Configure and register your first microservice above to issue its ingestion key.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="integrations-table">
              <thead>
                <tr>
                  <th>Application Name</th>
                  <th>Tech Stack</th>
                  <th>Environment</th>
                  <th>Slack Channel</th>
                  <th>Ingestion API Key</th>
                  <th>Alert Triggers</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {integrations.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <div className="app-cell">
                        <span className="app-dot"></span>
                        <span className="app-title">{item.applicationName}</span>
                      </div>
                    </td>
                    <td>
                      <span className={`stack-pill stack-${item.techStack.toLowerCase()}`}>
                        {item.techStack}
                      </span>
                    </td>
                    <td>
                      <span className={`env-pill ${item.environment.toLowerCase()}`}>
                        {item.environment}
                      </span>
                    </td>
                    <td>
                      <span className="channel-tag">{item.slackChannelName || "#all-devinsight"}</span>
                    </td>
                    <td>
                      <div className="api-key-cell">
                        <code className="api-key-code">
                          {revealedKeys[item.id]
                            ? item.apiKey
                            : `${item.apiKey?.substring(0, 10)}••••••••••••`}
                        </code>
                        <button
                          type="button"
                          className="key-action-btn"
                          title={revealedKeys[item.id] ? "Hide API Key" : "Reveal API Key"}
                          onClick={() => toggleKeyVisibility(item.id)}
                        >
                          {revealedKeys[item.id] ? <EyeOff size={13} /> : <Eye size={13} />}
                        </button>
                        <button
                          type="button"
                          className="key-action-btn"
                          title="Copy API Key"
                          onClick={() => copyApiKey(item.id, item.apiKey)}
                        >
                          {copiedKeyId === item.id ? (
                            <Check size={13} className="text-green" />
                          ) : (
                            <Copy size={13} />
                          )}
                        </button>
                      </div>
                    </td>
                    <td>
                      <span className="filter-badge">
                        {item.alertOnAnomaly ? "Anomaly" : ""} {item.alertOnError ? "+ Error" : ""}
                      </span>
                    </td>
                    <td>
                      <div className="action-buttons">
                        <button
                          className="test-action-btn"
                          title="Simulate Error & Test Slack Alert"
                          onClick={() => handleRunTest(item.applicationName)}
                          disabled={testing}
                        >
                          <Zap size={13} />
                          <span>Test</span>
                        </button>
                        <button
                          className="delete-action-btn"
                          title="Delete Integration"
                          onClick={() => handleDelete(item.id)}
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default Integrations;

