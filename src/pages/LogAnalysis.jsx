import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { analyzeLog, getLogById } from "../api/logApi";

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

  if (loadingLog) {
    return <p>Loading log...</p>;
  }

  if (!log) {
    return (
      <div>
        <p>{error || "Log not found."}</p>

        <button onClick={() => navigate("/dashboard")}>
          Back to Dashboard
        </button>
      </div>
    );
  }

  return (
    <div>
      <button onClick={() => navigate("/dashboard")}>
        ← Back to Dashboard
      </button>

      <h1>Analyze Log</h1>

      <h2>Log #{id}</h2>

      <div>
        <h3>Log Details</h3>

        <p>
          <strong>Application:</strong>{" "}
          {log.applicationName}
        </p>

        <p>
          <strong>Source:</strong> {log.source}
        </p>

        <p>
          <strong>Environment:</strong>{" "}
          {log.environment}
        </p>

        <p>
        <strong>Log Message:</strong>
      </p>

      <pre>{log.message}</pre>
      </div>

      <hr />

      <div>
        <h2>AI Analysis</h2>

        <div>
          <label htmlFor="model">
            <strong>LLM Model</strong>
          </label>

          <select
            id="model"
            value={model}
            onChange={(event) => setModel(event.target.value)}
          >
            <option value="phi3:mini">phi3:mini</option>
            <option value="llama3">llama3</option>
            <option value="mistral">mistral</option>
            <option value="phi4">phi4</option>
          </select>
        </div>

        <div>
          <h3>Analysis Types</h3>

          <label>
            <input
              type="checkbox"
              checked={analysisTypes.includes("SUMMARY")}
              onChange={() =>
                handleAnalysisTypeChange("SUMMARY")
              }
            />
            Summary
          </label>

          <br />

          <label>
            <input
              type="checkbox"
              checked={analysisTypes.includes("ROOT_CAUSE")}
              onChange={() =>
                handleAnalysisTypeChange("ROOT_CAUSE")
              }
            />
            Root Cause
          </label>

          <br />

          <label>
            <input
              type="checkbox"
              checked={analysisTypes.includes("SUGGESTED_FIX")}
              onChange={() =>
                handleAnalysisTypeChange("SUGGESTED_FIX")
              }
            />
            Suggested Fix
          </label>
        </div>

        {error && <p>{error}</p>}

        <button
          onClick={handleAnalyze}
          disabled={loadingAnalysis || analysisTypes.length === 0}
        >
          {loadingAnalysis
            ? "Analyzing..."
            : "Analyze Log"}
        </button>
      </div>

      {result && (
        <>
          <hr />

          <div>
            <h2>Analysis Result</h2>

            {result.summary && (
              <div>
                <h3>Summary</h3>
                <p>{result.summary}</p>
              </div>
            )}

            {result.probableRootCause && (
              <div>
                <h3>Probable Root Cause</h3>
                <p>{result.probableRootCause}</p>
              </div>
            )}

            {result.rootCause && (
              <div>
                <h3>Root Cause</h3>
                <p>{result.rootCause}</p>
              </div>
            )}

            {result.severity && (
              <div>
                <h3>Severity</h3>
                <p>{result.severity}</p>
              </div>
            )}

            {result.suggestedFix && (
              <div>
                <h3>Suggested Fix</h3>
                <p>{result.suggestedFix}</p>
              </div>
            )}

            {result.recommendation && (
              <div>
                <h3>Recommendation</h3>
                <p>{result.recommendation}</p>
              </div>
            )}

            {result.confidenceScore !== undefined && (
              <div>
                <h3>Confidence Score</h3>
                <p>{result.confidenceScore}</p>
              </div>
            )}

            {result.recommendedActions &&
              result.recommendedActions.length > 0 && (
                <div>
                  <h3>Recommended Actions</h3>

                  <ul>
                    {result.recommendedActions.map(
                      (action, index) => (
                        <li key={index}>{action}</li>
                      )
                    )}
                  </ul>
                </div>
              )}
          </div>
        </>
      )}
    </div>
  );
};

export default LogAnalysis;