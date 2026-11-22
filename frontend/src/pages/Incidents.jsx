import { useCallback, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { changeIncidentStatus, incidentDetail, incidentDiagnosis, listIncidentApplications, listIncidentLogs,
  listIncidents, retryIncidentDiagnosis } from "../api/incidentApi";
import { ApplicationSelect, RangeSelect, Refresh, ResourceError, Status } from "../components/WorkspaceControls";
import { useWorkspace } from "../context/workspace";
import useResource, { readError } from "../hooks/useResource";
import { boundedPage, dateTime, label, rangeKey, ranges } from "../observability";
import IncidentNotifications from "./IncidentNotifications";

const reasons = {
  SUPPLIED_TRACE: "Supplied trace", APPLICATION_SIGNATURE: "Application failure signature",
  INITIAL_SEED: "First qualifying event", SAME_SUPPLIED_TRACE: "Same supplied trace",
  SAME_APPLICATION_SIGNATURE: "Same application failure signature", TRACE_CONTEXT: "Trace context",
};
const canPollDiagnosis = (job) => job === null || ["PENDING","RUNNING","RETRY_WAIT"].includes(job.status);

function Pagination({ page, hasNext, change, name }) {
  return <nav className="work-paging compact" aria-label={name}>
    <button className="work-button" disabled={page === 0} onClick={() => change(page - 1)}>Previous</button>
    <span>Page {page + 1}</span>
    <button className="work-button" disabled={!hasNext} onClick={() => change(page + 1)}>Next</button>
  </nav>;
}

function Diagnosis({ org, incident, canWrite }) {
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const load = useCallback(async (signal) => {
    try {
      return await incidentDiagnosis(org, incident, signal, page);
    } catch (failure) {
      if (failure.response?.status === 404) return null;
      throw failure;
    }
  }, [org, incident, page]);
  const job = useResource(`${org}/${incident}/diagnosis/${page}`, load, page === 0 ? 5000 : 0, canPollDiagnosis);
  const data = job.data;
  async function retry() {
    setBusy(true);
    setError("");
    try {
      await retryIncidentDiagnosis(org, incident);
      setPage(0);
    } catch (failure) {
      setError(readError(failure));
    } finally {
      job.refresh();
      setBusy(false);
    }
  }
  return <section className="work-panel incident-section" aria-label="AI diagnosis">
    <h3>AI diagnosis</h3>
    <ResourceError resource={job} />
    {data === undefined && !job.error && <p role="status">Loading diagnosis…</p>}
    {data === null && <p className="work-muted">No automatic diagnosis scheduled.</p>}
    {data && <>
      <p><Status value={data.status}>{data.leaseExpired ? "Awaiting worker recovery" : label(data.status)}</Status>
        {" · "}Attempts {data.attemptCount} of {data.maxAttempts}</p>
      {data.status === "RETRY_WAIT" && <p>Next retry {dateTime(data.nextAttemptAt)}</p>}
      {data.failureCategory && <p>Last failure · {label(data.failureCategory)}</p>}
      {data.status === "SUCCEEDED" && <dl className="work-properties">
        {data.summary && <div><dt>Summary</dt><dd className="work-prose">{data.summary}</dd></div>}
        {data.rootCause && <div><dt>Probable cause</dt><dd className="work-prose">{data.rootCause}</dd></div>}
        {data.recommendation && <div><dt>Suggested remediation</dt><dd className="work-prose">{data.recommendation}</dd></div>}
        {data.completedAt && <div><dt>Result accepted</dt><dd>{dateTime(data.completedAt)}</dd></div>}
      </dl>}
      {data.logId && <Link className="work-link" to={`/logs/${data.logId}/analyze`}>View diagnosis source</Link>}
      {canWrite && data.status === "FAILED_TERMINAL" && data.manualRetries < 3 &&
        <button className="work-button" disabled={busy} onClick={retry}>Retry diagnosis</button>}
      {data.attempts.length > 0 && <details className="work-details">
        <summary>Diagnosis attempts</summary>
        <ul>{data.attempts.map((attempt) => <li key={attempt.number}>
          Attempt {attempt.number} · {label(attempt.outcome || "RUNNING")} · started {dateTime(attempt.startedAt)}
          {attempt.finishedAt && ` · finished ${dateTime(attempt.finishedAt)}`}
          {attempt.failureCategory && ` · ${label(attempt.failureCategory)}`}
        </li>)}</ul>
        {(page > 0 || data.hasMore) && <Pagination page={page} hasNext={data.hasMore} change={setPage} name="Diagnosis attempt pages" />}
      </details>}
    </>}
    {error && <p role="alert" className="work-alert">{error}</p>}
  </section>;
}

function Evidence({ org, incident }) {
  const [page, setPage] = useState(0);
  const [appsPage, setAppsPage] = useState(0);
  const load = useCallback((signal) => listIncidentLogs(org, incident, page, signal), [org, incident, page]);
  const loadApps = useCallback((signal) => listIncidentApplications(org, incident, appsPage, signal), [org, incident, appsPage]);
  const logs = useResource(`${org}/${incident}/logs/${page}`, load, 20000);
  const apps = useResource(`${org}/${incident}/applications/${appsPage}`, loadApps, 20000);
  return <section className="work-panel incident-section">
    <h3>Correlation evidence</h3>
    <p className="work-muted">Membership groups observed logs. Reported trace context shows only spans present in those logs.</p>
    <ResourceError resource={apps} /><ResourceError resource={logs} />
    {apps.data && <div><h4>Affected applications</h4>
      <ul className="affected-apps">{apps.data.content.map((item) => <li key={JSON.stringify([item.name,item.environment])}>
        {item.name} · {label(item.environment)} · {item.events} events
      </li>)}</ul>
      {(appsPage > 0 || apps.data.hasNext) && <Pagination page={appsPage} hasNext={apps.data.hasNext} change={setAppsPage} name="Affected application pages" />}
    </div>}
    <header className="work-panel-header"><h4>Source log timeline</h4>
      <Link className="work-link" to={`/logs?incident=${incident}&range=MONTH`}>Open in log explorer</Link></header>
    {!logs.data && !logs.error && <p role="status">Loading source events…</p>}
    {logs.data && <>
      <div className="work-table-scroll" tabIndex={0} role="region" aria-label="Correlation timeline">
        <table className="work-table"><thead><tr>{["Occurrence","Log","Level","Association","Reported trace","Membership recorded"].map((item) =>
          <th scope="col" key={item}>{item}</th>)}</tr></thead>
          <tbody>{logs.data.content.map((log) => <tr key={log.logId}>
            <td>{dateTime(log.timestamp)}</td>
            <td><Link className="work-link" to={`/logs/${log.logId}/analyze`}>Log {log.logId}</Link><small>{log.application}</small></td>
            <td><Status value={log.level} /></td><td>{reasons[log.reason] ?? label(log.reason)}</td>
            <td>{log.traceparent ? <>
              <Link className="work-link" to={`/logs?trace=${log.traceparent.slice(3,35)}&range=MONTH`}>
                <code>{log.traceparent.slice(3,15)}…</code></Link>
              <small>Span {log.traceparent.slice(36,52)} · {label(log.traceOrigin)}</small>
            </> : "—"}</td><td>{dateTime(log.linkedAt)}</td>
          </tr>)}</tbody>
        </table>
      </div>
      <Pagination page={page} hasNext={logs.data.hasNext} change={setPage} name="Timeline pages" />
    </>}
  </section>;
}

function IncidentDetail({ org, incident, canWrite, onClose, onChange }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const load = useCallback((signal) => incidentDetail(org, incident, signal), [org, incident]);
  const resource = useResource(`${org}/${incident}`, load, 15000);
  const detail = resource.data;
  async function transition(action) {
    setError("");
    setBusy(true);
    try {
      await changeIncidentStatus(org, incident, action, detail.version);
    } catch (failure) {
      setError(readError(failure));
    } finally {
      setBusy(false);
      resource.refresh();
      onChange();
    }
  }
  return <section className="incident-workspace" aria-label="Incident details">
    <header className="work-panel work-panel-header">
      <div><h2>{detail ? `${detail.representativeApplication} incident` : "Incident details"}</h2>
        <p className="work-muted"><code>{incident}</code></p></div>
      <button className="work-button" onClick={onClose}>Close details</button>
    </header>
    <ResourceError resource={resource} />
    {!detail && !resource.error && <p role="status">Loading incident…</p>}
    {detail && <>
      <section className="work-panel incident-section">
        <div className="work-panel-header"><h3>Incident overview</h3>
          <div><Status value={detail.severity} /> <Status value={detail.status} /></div></div>
        <dl className="work-properties">
          <div><dt>First occurrence</dt><dd>{dateTime(detail.firstEventAt)}</dd></div>
          <div><dt>Last occurrence</dt><dd>{dateTime(detail.lastEventAt)}</dd></div>
          <div><dt>Incident created</dt><dd>{dateTime(detail.createdAt)}</dd></div>
          <div><dt>Last update</dt><dd>{dateTime(detail.updatedAt)}</dd></div>
          <div><dt>Associated events</dt><dd>{detail.eventCount}</dd></div>
          <div><dt>Correlation rule</dt><dd>{reasons[detail.correlationRule] ?? label(detail.correlationRule)} · v{detail.ruleVersion}</dd></div>
          {detail.acknowledgedAt && <div><dt>Acknowledged</dt><dd>{dateTime(detail.acknowledgedAt)}</dd></div>}
          {detail.resolvedAt && <div><dt>Resolved</dt><dd>{dateTime(detail.resolvedAt)}</dd></div>}
        </dl>
        {canWrite && detail.status !== "RESOLVED" && <div className="incident-work-actions">
          {detail.status === "OPEN" && <button className="work-button" disabled={busy} onClick={() => transition("acknowledge")}>Acknowledge</button>}
          <button className="work-button" disabled={busy} onClick={() => transition("resolve")}>Resolve</button>
        </div>}
        {error && <p role="alert" className="work-alert">{error}</p>}
      </section>
      <div className="incident-work-panels">
        <Diagnosis org={org} incident={incident} canWrite={canWrite} />
        <IncidentNotifications organization={org} incident={incident} canRetry={canWrite} />
      </div>
      <Evidence org={org} incident={incident} />
    </>}
  </section>;
}

export default function Incidents() {
  const { organization, applications } = useWorkspace();
  const [filters, setFilters] = useSearchParams();
  const org = organization.id;
  const status = filters.get("status") || "";
  const severity = filters.get("severity") || "";
  const application = filters.get("application") || "";
  const range = rangeKey(filters.get("range"));
  const selected = filters.get("incident");
  const page = boundedPage(filters.get("page"));
  const load = useCallback((signal) => {
    const now = Date.now();
    const duration = ranges.find(([key]) => key === range)[2];
    return listIncidents(org, { page, status: status || undefined, severity: severity || undefined,
      applicationId: application || undefined, from: new Date(now - duration).toISOString(),
      to: new Date(now).toISOString() }, signal);
  }, [org, page, status, severity, application, range]);
  const list = useResource(JSON.stringify([org,page,status,severity,application,range]), load, 15000);
  function update(key, value, reset = true) {
    setFilters((current) => {
      const next = new URLSearchParams(current);
      if (reset) next.delete("page");
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  }
  return <div className="incident-workspace">
    <section className="work-panel" aria-label="Incident list">
      <div className="work-toolbar">
        <RangeSelect value={range} onChange={(value) => update("range", value)} />
        <ApplicationSelect applications={applications} value={application} onChange={(value) => update("application", value)} />
        {[["Status","status",status,["OPEN","ACKNOWLEDGED","RESOLVED"]],["Severity","severity",severity,["MEDIUM","HIGH","CRITICAL"]]]
          .map(([title,key,value,options]) => <label key={key} className="work-field">{title}
            <select value={value} onChange={(event) => update(key,event.target.value)}>
              <option value="">Any</option>
              {options.map((option) => <option key={option} value={option}>{label(option)}</option>)}
            </select></label>)}
        <Refresh resource={list} />
      </div>
      <p className="work-muted">Newest last occurrence first</p>
      <ResourceError resource={list} />
      {!list.data && !list.error && <p className="work-loading" role="status">Loading incidents…</p>}
      {list.data?.content.length > 0 && <div className="work-table-scroll" tabIndex={0} role="region" aria-label="Incidents">
        <table className="work-table"><thead><tr>{["Incident","Severity","Status","Events","First occurrence","Last occurrence","Diagnosis","Updated"].map((item) =>
          <th key={item} scope="col">{item}</th>)}</tr></thead><tbody>{list.data.content.map((item) => <tr key={item.id}>
          <td><button className="work-text-button" onClick={() => update("incident",item.id,false)}>{item.representativeApplication}</button>
            <small>{item.id.slice(0,8)}</small></td>
          <td><Status value={item.severity} /></td><td><Status value={item.status} /></td><td>{item.eventCount}</td>
          <td>{dateTime(item.firstEventAt)}</td><td>{dateTime(item.lastEventAt)}</td>
          <td>{item.diagnosisStatus ? <Status value={item.diagnosisStatus} /> : "Unscheduled"}</td><td>{dateTime(item.updatedAt)}</td>
        </tr>)}</tbody></table>
      </div>}
      {list.data && (list.data.content.length === 0 ? <p className="work-empty">No incidents match these filters.</p> : null)}
      {list.data && <Pagination page={page} hasNext={page < 200 && list.data.hasNext} change={(value) => update("page",value,false)} name="Incident pages" />}
    </section>
    {selected && <IncidentDetail key={selected} org={org} incident={selected} canWrite={organization.role !== "VIEWER"}
      onClose={() => update("incident","",false)} onChange={list.refresh} />}
  </div>;
}
