export const ranges = [
  ["HOUR", "Last hour", 3600000],
  ["DAY", "Last 24 hours", 86400000],
  ["WEEK", "Last 7 days", 604800000],
  ["MONTH", "Last 30 days", 2592000000],
];

export const levels = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"];

export function rangeKey(value) {
  return ranges.some(([key]) => key === value) ? value : "DAY";
}

export function boundedPage(value) {
  const page = Number(value);
  return Number.isInteger(page) && page >= 0 && page <= 200 ? page : 0;
}

export function label(value) {
  if (!value) return "Unavailable";
  return ({
    SUCCEEDED: "Completed", RETRY_WAIT: "Retry scheduled", FAILED_TERMINAL: "Terminal failure",
    DELIVERY_UNKNOWN: "Delivery unknown", V2_NORMAL: "V2 normal", V2_ANOMALY: "V2 anomaly",
    SLACK_ACKNOWLEDGED: "Slack acknowledged", INCIDENT_CREATED: "Incident created",
    INCIDENT_ACKNOWLEDGED: "Incident acknowledged", INCIDENT_RESOLVED: "Incident resolved",
    DIAGNOSIS_COMPLETED: "Diagnosis completed", OPEN: "Open", ACKNOWLEDGED: "Acknowledged",
    RESOLVED: "Resolved", CRITICAL: "Critical", HIGH: "High", MEDIUM: "Medium",
  })[value] ?? value.toLowerCase().replaceAll("_", " ").replace(/^./, (initial) => initial.toUpperCase());
}

export const number = new Intl.NumberFormat().format;
export const dateTime = (value) => value ? new Date(value).toLocaleString(undefined, {
  dateStyle: "medium", timeStyle: "short",
}) : "—";

export function signalTotals(buckets) {
  return buckets.reduce((total, bucket) => ({
    logs: total.logs + bucket.logs,
    anomalies: total.anomalies + bucket.anomalies,
    errors: total.errors + bucket.errors,
    failures: total.failures + bucket.scoringFailures,
  }), { logs: 0, anomalies: 0, errors: 0, failures: 0 });
}
