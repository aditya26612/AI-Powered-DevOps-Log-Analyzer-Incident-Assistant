import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { changeIncidentStatus, incidentDetail, incidentDiagnosis, listIncidentLogs, listIncidents,
  retryIncidentDiagnosis } from "../api/incidentApi";
import { listApplications, listOrganizations, selectionKey } from "../api/integrationApi";
import "./Incidents.css";

const time = (value) => new Date(value).toLocaleString();
const labels = {
  OPEN: "Open", ACKNOWLEDGED: "Acknowledged", RESOLVED: "Resolved",
  MEDIUM: "Medium", HIGH: "High", CRITICAL: "Critical",
  SUPPLIED_TRACE: "Supplied trace", APPLICATION_SIGNATURE: "Application failure signature",
  INITIAL_SEED: "First qualifying event", SAME_SUPPLIED_TRACE: "Same supplied trace",
  SAME_APPLICATION_SIGNATURE: "Same application failure signature", TRACE_CONTEXT: "Trace context",
  PENDING: "Pending", PROCESSING: "Processing", COMPLETED: "Completed", FAILED: "Failed",
  RUNNING: "Processing", RETRY_WAIT: "Retry scheduled", SUCCEEDED: "Completed", FAILED_TERMINAL: "Terminal failure",
};

function Pagination({ data, setPage }) {
  if (!data) return null;
  return <div className="incident-pagination">
    <button type="button" disabled={data.page === 0} onClick={() => setPage(data.page - 1)}>Previous</button>
    <span>Page {data.page + 1}</span>
    <button type="button" disabled={!data.hasNext} onClick={() => setPage(data.page + 1)}>Next</button>
  </div>;
}

export default function Incidents() {
  const [organizations, setOrganizations] = useState([]);
  const [organization, setOrganization] = useState(localStorage.getItem(selectionKey) || "");
  const [applications, setApplications] = useState([]);
  const [status, setStatus] = useState("");
  const [severity, setSeverity] = useState("");
  const [application, setApplication] = useState("");
  const [page, setPage] = useState(0);
  const [list, setList] = useState(null);
  const [selected, setSelected] = useState("");
  const [detail, setDetail] = useState(null);
  const [diagnosis, setDiagnosis] = useState(null);
  const [logs, setLogs] = useState(null);
  const [logsPage, setLogsPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState("");
  const [updating, setUpdating] = useState(false);
  const role = organizations.find(({ id }) => id === organization)?.role;

  useEffect(() => {
    let active = true;
    listOrganizations().then((data) => {
      if (active) setOrganizations(data);
    }).catch(() => { if (active) setError("Could not load organizations."); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!organization) return;
    let active = true;
    listApplications(organization).then((data) => {
      if (active) setApplications(data);
    }).catch(() => { if (active) setError("Could not load applications."); });
    return () => { active = false; };
  }, [organization]);

  useEffect(() => {
    if (!organization) return;
    const controller = new AbortController();
    const params = { page, status: status || undefined, severity: severity || undefined, applicationId: application || undefined };
    listIncidents(organization, params, controller.signal).then((data) => {
      if (!controller.signal.aborted) setList(data);
    }).catch(() => { if (!controller.signal.aborted) setError("Could not load incidents."); });
    return () => controller.abort();
  }, [organization, page, status, severity, application, revision]);

  useEffect(() => {
    if (!organization || !selected) return;
    const controller = new AbortController();
    Promise.all([incidentDetail(organization, selected, controller.signal),
      listIncidentLogs(organization, selected, logsPage, controller.signal)]).then(([incident, members]) => {
      if (!controller.signal.aborted) {
        setDetail(incident);
        setLogs(members);
      }
    }).catch(() => { if (!controller.signal.aborted) setError("Could not load this incident."); });
    return () => controller.abort();
  }, [organization, selected, logsPage, revision]);

  useEffect(() => {
    if (!organization || !selected) return;
    const controller = new AbortController();
    let next;
    async function refreshDiagnosis() {
      try {
        const data = await incidentDiagnosis(organization, selected, controller.signal);
        if (controller.signal.aborted) return;
        setDiagnosis(data);
        if (["PENDING", "RUNNING", "RETRY_WAIT"].includes(data.status)) {
          next = setTimeout(refreshDiagnosis, 1500);
        }
      } catch (failure) {
        if (!controller.signal.aborted && failure.response?.status !== 404) setError("Could not load diagnosis status.");
      }
    }
    refreshDiagnosis();
    return () => {
      controller.abort();
      clearTimeout(next);
    };
  }, [organization, selected, revision]);

  function selectOrganization(next) {
    if (next) localStorage.setItem(selectionKey, next);
    setOrganization(next);
    setApplication("");
    setApplications([]);
    setList(null);
    setPage(0);
    show("");
  }

  function show(id) {
    setError("");
    setSelected(id);
    setDetail(null);
    setDiagnosis(null);
    setLogs(null);
    setLogsPage(0);
  }

  function filter(setValue, value) {
    setError("");
    setList(null);
    setPage(0);
    setValue(value);
  }

  async function transition(action) {
    setUpdating(true);
    setError("");
    try {
      await changeIncidentStatus(organization, detail.id, action, detail.version);
    } catch (failure) {
      setError(failure.response?.status === 409 ? "Incident changed. Review the latest state and try again." : "Could not change the incident status.");
    } finally {
      setUpdating(false);
      setRevision((value) => value + 1);
    }
  }

  async function retryDiagnosis() {
    setUpdating(true);
    setError("");
    try {
      setDiagnosis(await retryIncidentDiagnosis(organization, detail.id));
    } catch {
      setError("Could not schedule another diagnosis attempt.");
    } finally {
      setUpdating(false);
      setRevision((value) => value + 1);
    }
  }

  return <section className="incidents-page">
    <h1>Incidents</h1>
    {error && <p role="alert">{error}</p>}
    <div className="incident-filters">
      <label>Organization
        <select value={organization} onChange={(event) => selectOrganization(event.target.value)} disabled={updating}>
          <option value="" disabled>Select an organization</option>
          {organizations.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}
        </select>
      </label>
      <label>Status
        <select value={status} onChange={(event) => filter(setStatus, event.target.value)}>
          <option value="">All statuses</option>
          {["OPEN", "ACKNOWLEDGED", "RESOLVED"].map((value) => <option key={value} value={value}>{labels[value]}</option>)}
        </select>
      </label>
      <label>Severity
        <select value={severity} onChange={(event) => filter(setSeverity, event.target.value)}>
          <option value="">All severities</option>
          {["MEDIUM", "HIGH", "CRITICAL"].map((value) => <option key={value} value={value}>{labels[value]}</option>)}
        </select>
      </label>
      <label>Application
        <select value={application} onChange={(event) => filter(setApplication, event.target.value)}>
          <option value="">All applications</option>
          {applications.map((item) => <option value={item.id} key={item.id}>{item.applicationName} · {item.environment}</option>)}
        </select>
      </label>
    </div>

    {organization && <>
      <div className="incident-table">
        <table>
          <thead><tr><th>Incident</th><th>Severity</th><th>Status</th><th>Events</th><th>Last occurrence</th></tr></thead>
          <tbody>{list?.content.map((item) => <tr key={item.id}>
            <td><button type="button" disabled={updating} onClick={() => show(item.id)}>{item.representativeApplication}</button></td>
            <td>{labels[item.severity]}</td><td>{labels[item.status]}</td><td>{item.eventCount}</td><td>{time(item.lastEventAt)}</td>
          </tr>)}</tbody>
        </table>
      </div>
      {!list && !error && <p role="status">Loading incidents…</p>}
      {list?.content.length === 0 && <p>No incidents match the selected filters.</p>}
      <Pagination data={list} setPage={setPage} />
    </>}

    {detail && <section className="incident-details" aria-label="Incident details">
      <h2>{detail.representativeApplication} incident</h2>
      <dl>
        <div><dt>Status</dt><dd>{labels[detail.status]}</dd></div>
        <div><dt>Severity</dt><dd>{labels[detail.severity]}</dd></div>
        <div><dt>First occurrence</dt><dd>{time(detail.firstEventAt)}</dd></div>
        <div><dt>Last occurrence</dt><dd>{time(detail.lastEventAt)}</dd></div>
        <div><dt>Associated events</dt><dd>{detail.eventCount}</dd></div>
        <div><dt>Rule</dt><dd>{labels[detail.correlationRule]}</dd></div>
        <div><dt>Diagnosis</dt><dd>{diagnosis?.leaseExpired ? "Awaiting worker recovery" :
          labels[diagnosis?.status || detail.diagnosisStatus] || "No automatic diagnosis scheduled"}
          {detail.diagnosisLogId && <> · <Link to={`/logs/${detail.diagnosisLogId}/analyze`}>Diagnosis log</Link></>}
        </dd></div>
      </dl>
      {diagnosis && <div>
        <p>Attempts {diagnosis.attemptCount} of {diagnosis.maxAttempts}</p>
        {diagnosis.nextAttemptAt && diagnosis.status === "RETRY_WAIT" && <p>Next retry {time(diagnosis.nextAttemptAt)}</p>}
        {diagnosis.failureCategory && <p>Last failure {diagnosis.failureCategory.toLowerCase().replaceAll("_", " ")}</p>}
        {diagnosis.summary && <p>{diagnosis.summary}</p>}
        {role && role !== "VIEWER" && diagnosis.status === "FAILED_TERMINAL" && diagnosis.manualRetries < 3 &&
          <button type="button" disabled={updating} onClick={retryDiagnosis}>Retry diagnosis</button>}
      </div>}
      {role && role !== "VIEWER" && detail.status !== "RESOLVED" && <div className="incident-actions">
        {detail.status === "OPEN" && <button type="button" disabled={updating} onClick={() => transition("acknowledge")}>Acknowledge</button>}
        <button type="button" disabled={updating} onClick={() => transition("resolve")}>Resolve</button>
      </div>}
      <h3>Source logs</h3>
      <div className="incident-table">
        <table>
          <thead><tr><th>Log</th><th>Application</th><th>Occurrence</th><th>Level</th><th>Association</th></tr></thead>
          <tbody>{logs?.content.map((log) => <tr key={log.logId}>
            <td><Link to={`/logs/${log.logId}/analyze`}>Log {log.logId}</Link></td><td>{log.application}</td>
            <td>{time(log.timestamp)}</td><td>{log.level}</td><td>{labels[log.reason]}</td>
          </tr>)}</tbody>
        </table>
      </div>
      <Pagination data={logs} setPage={setLogsPage} />
    </section>}
  </section>;
}
