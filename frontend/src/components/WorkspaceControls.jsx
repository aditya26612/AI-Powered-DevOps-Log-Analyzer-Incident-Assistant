import { RefreshCw } from "lucide-react";
import { dateTime, label, ranges } from "../observability";

export function RangeSelect({ value, onChange }) {
  return <label className="work-field">Time range
    <select value={value} onChange={(event) => onChange(event.target.value)}>
      {ranges.map(([key, title]) => <option value={key} key={key}>{title}</option>)}
    </select>
  </label>;
}

export function ApplicationSelect({ applications, value, onChange }) {
  return <label className="work-field">Application
    <select value={value} onChange={(event) => onChange(event.target.value)}>
      <option value="">All applications</option>
      {applications.map((item) => <option key={item.id} value={item.id}>{item.applicationName} · {label(item.environment)}</option>)}
    </select>
  </label>;
}

export function Refresh({ resource }) {
  return <div className="work-refresh">
    <span>{resource.error ? "Refresh failed" : resource.updatedAt ? `Updated ${dateTime(resource.updatedAt)}` : "Waiting for data"}</span>
    <button type="button" className="work-button" onClick={resource.refresh}>
      <RefreshCw size={15} aria-hidden="true" /> Refresh
    </button>
  </div>;
}

export function ResourceError({ resource }) {
  return resource.error && <p role="alert" className="work-alert">
    {resource.error} {resource.data && "Previously loaded data is shown."}
  </p>;
}

export function Status({ value, children }) {
  const tone = ["FATAL","ERROR","CRITICAL","FAILED","FAILED_TERMINAL"].includes(value) ? "danger"
    : ["WARN","HIGH","V2_ANOMALY","DELIVERY_UNKNOWN","RETRY_WAIT"].includes(value) ? "warning"
      : ["DELIVERED","SUCCEEDED","COMPLETED","RESOLVED","V2_NORMAL"].includes(value) ? "success" : "neutral";
  return <span className={`work-status ${tone}`}>{children ?? label(value)}</span>;
}
