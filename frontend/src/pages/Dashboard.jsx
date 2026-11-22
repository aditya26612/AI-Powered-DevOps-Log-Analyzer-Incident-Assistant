import { useCallback, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Activity, ArrowRight, Bell, CircleAlert, Database, Radio, Siren } from "lucide-react";
import { getSummary } from "../api/observabilityApi";
import { ApplicationSelect, RangeSelect, Refresh, ResourceError, Status } from "../components/WorkspaceControls";
import { useWorkspace } from "../context/workspace";
import useResource from "../hooks/useResource";
import { dateTime, label, levels, number, rangeKey, signalTotals } from "../observability";

function Metric({ icon: Icon, title, amount, hint, tone, href }) {
  return <Link to={href} className={`work-panel metric-card ${tone}`}>
    <span className="metric-icon"><Icon size={19} aria-hidden="true" /></span>
    <span className="metric-title">{title}</span>
    <strong>{number(amount)}</strong>
    <small>{hint}</small>
    <ArrowRight size={15} className="metric-arrow" aria-hidden="true" />
  </Link>;
}

function StatusCounts({ title, counts, empty }) {
  return <section className="work-panel state-panel">
    <h2>{title}</h2>
    {Object.keys(counts).length ? <dl className="state-list">
      {Object.entries(counts).sort().map(([key,value]) => <div key={key}>
        <dt><Status value={key} /></dt><dd>{number(value)}</dd>
      </div>)}
    </dl> : <p className="work-muted">{empty}</p>}
  </section>;
}

function Volume({ data }) {
  const [mode, setMode] = useState("logs");
  const [showTable, setShowTable] = useState(false);
  const name = mode === "logs" ? "Persisted logs" : "V2 anomalies";
  const peak = Math.max(0, ...data.buckets.map((bucket) => bucket[mode]));
  const max = Math.max(1, peak);
  const marks = Array.from(new Set([max, Math.round(max * .75), Math.round(max * .5), Math.round(max * .25), 0]));
  return <section className="work-panel volume-panel">
    <header className="work-panel-header">
      <div><h2>Event volume</h2><p className="work-muted">Source occurrence time · local display</p></div>
      <div className="work-segment" aria-label="Event series">
        <button type="button" aria-pressed={mode === "logs"} onClick={() => setMode("logs")}>Logs</button>
        <button type="button" aria-pressed={mode === "anomalies"} onClick={() => setMode("anomalies")}>Anomalies</button>
      </div>
    </header>
    {peak > 0 ? <div className="volume-plot" aria-hidden="true">
      <div className="volume-scale">{marks.map((mark) => <span key={mark}>{number(mark)}</span>)}</div>
      <div className="volume-bars">
        {data.buckets.map((bucket) => <div key={bucket.start} className="volume-column"
          title={`${dateTime(bucket.start)} · ${number(bucket[mode])} ${name.toLowerCase()}`}>
          <span style={{ height: `${100 * bucket[mode] / max}%` }} />
        </div>)}
      </div>
    </div> : <p className="work-chart-empty">{mode === "logs" ? "No logs in this time range." : "No V2 anomalies in this time range."}</p>}
    <div className="volume-footer">
      <span>{dateTime(data.from)}</span>
      <button type="button" className="work-text-button" onClick={() => setShowTable((shown) => !shown)}
        aria-expanded={showTable}>{showTable ? "Hide values" : "View values"}</button>
      <span>{dateTime(data.to)}</span>
    </div>
    {showTable && <div className="work-table-scroll" tabIndex={0} role="region" aria-label="Event volume values">
      <table className="work-table"><thead><tr><th scope="col">Bucket start</th><th scope="col">{name}</th></tr></thead>
        <tbody>{data.buckets.map((item) => <tr key={item.start}><td>{dateTime(item.start)}</td><td>{number(item[mode])}</td></tr>)}</tbody>
      </table>
    </div>}
  </section>;
}

function Severity({ counts }) {
  const total = Object.values(counts).reduce((sum, value) => sum + value, 0);
  return <section className="work-panel severity-panel">
    <h2>Log severity</h2>
    <p className="work-muted">Normalized source levels</p>
    {total ? <ul className="severity-list">{levels.toReversed().map((level) => <li key={level}>
      <span>{label(level)}</span><span className={`severity-track ${level.toLowerCase()}`}>
        <span style={{ width: `${100 * (counts[level] ?? 0) / total}%` }} />
      </span><strong>{number(counts[level] ?? 0)}</strong>
    </li>)}</ul> : <p className="work-chart-empty">Severity data appears when logs arrive.</p>}
  </section>;
}

export default function Dashboard() {
  const { organization, applications, applicationError } = useWorkspace();
  const [search, setSearch] = useSearchParams();
  const range = rangeKey(search.get("range"));
  const applicationId = search.get("application") || "";
  const org = organization.id;
  const load = useCallback((signal) => getSummary(org, { range, applicationId: applicationId || undefined }, signal),
    [org, range, applicationId]);
  const resource = useResource(`${org}/${range}/${applicationId}`, load, 20000);
  const data = resource.data;
  const scope = new URLSearchParams({ range, ...(applicationId && { application: applicationId }) });
  const logLink = (extra) => `/logs?${new URLSearchParams([...scope, ...Object.entries(extra ?? {})])}`;
  const incidentLink = `/incidents?${scope}`;
  function filter(key, value) {
    setSearch((previous) => {
      const next = new URLSearchParams(previous);
      if (value) next.set(key, value);
      else next.delete(key);
      return next;
    });
  }
  const totals = data && signalTotals(data.buckets);
  return <div className="observability-overview">
    <div className="work-toolbar">
      <RangeSelect value={range} onChange={(value) => filter("range", value)} />
      <ApplicationSelect applications={applications} value={applicationId} onChange={(value) => filter("application", value)} />
      <Refresh resource={resource} />
    </div>
    {applicationError && <p role="alert" className="work-alert">The application filter could not load.</p>}
    <ResourceError resource={resource} />
    {!data && !resource.error && <div className="work-loading" role="status">Loading observability metrics…</div>}
    {data && <>
      <div className="metric-grid">
        <Metric icon={Database} title="Persisted logs" amount={totals.logs} hint="Source events in range" tone="blue" href={logLink()} />
        <Metric icon={Activity} title="V2 anomalies" amount={totals.anomalies} hint="Completed V2 classifications" tone="violet" href={logLink({ classification: "V2_ANOMALY" })} />
        <Metric icon={CircleAlert} title="Operational errors" amount={totals.errors} hint="ERROR or FATAL severity" tone="amber" href={logLink({ operationalErrors: "true" })} />
        <Metric icon={Siren} title="Open incidents" amount={data.incidents.OPEN ?? 0}
          hint={`${number(data.incidents.ACKNOWLEDGED ?? 0)} acknowledged in range`} tone="red" href={`${incidentLink}&status=OPEN`} />
        <Metric icon={Bell} title="Slack needs attention" amount={(data.deliveries.DELIVERY_UNKNOWN ?? 0) + (data.deliveries.FAILED_TERMINAL ?? 0)}
          hint="Unknown or terminal delivery" tone="amber" href={`?${scope}#delivery-states`} />
      </div>
      <div className="overview-charts"><Volume data={data} /><Severity counts={data.severity} /></div>
      <div className="overview-charts">
        <section className="work-panel">
          <header className="work-panel-header"><div><h2>Incident attention</h2>
            <p className="work-muted">Unresolved incidents, ordered by severity and last occurrence</p></div>
            <Link className="work-link" to={incidentLink}>All incidents <ArrowRight size={15} aria-hidden="true" /></Link>
          </header>
          {data.attention.length ? <div className="work-table-scroll" tabIndex={0} role="region" aria-label="Unresolved incidents">
            <table className="work-table"><thead><tr>
              <th scope="col">Primary application</th><th scope="col">Status</th><th scope="col">Diagnosis</th><th scope="col">Events</th>
            </tr></thead><tbody>{data.attention.map((item) => <tr key={item.id}>
              <td><Link className="work-link" to={`/incidents?incident=${item.id}`}>{item.application || "Incident"}</Link>
                <small>{dateTime(item.first)} — {dateTime(item.last)}</small></td>
              <td><Status value={item.severity} /> <Status value={item.status} /></td>
              <td>{item.diagnosisStatus ? <Status value={item.diagnosisStatus} /> : "Unscheduled"}</td>
              <td>{number(item.events)}</td>
            </tr>)}</tbody></table>
          </div> : <p className="work-empty">No unresolved incidents in this range.</p>}
        </section>
        <section className="work-panel">
          <h2>Recent activity</h2><p className="work-muted">Recorded lifecycle and acknowledgement times</p>
          {data.activity.length ? <ul className="activity-list">{data.activity.map((event, index) => <li key={event.type + event.time + index}>
            <Radio size={16} aria-hidden="true" /><div>
              {event.incidentId ? <Link className="work-link" to={`/incidents?incident=${event.incidentId}`}>{label(event.type)}</Link> : label(event.type)}
              <time dateTime={event.time}>{dateTime(event.time)}</time>
            </div></li>)}</ul> : <p className="work-empty">No recorded activity in this range.</p>}
        </section>
      </div>
      <div id="delivery-states" className="overview-states">
        <StatusCounts title="Diagnosis jobs" counts={data.diagnoses} empty="No diagnosis jobs created in this range." />
        <StatusCounts title="Slack deliveries" counts={data.deliveries} empty="No Slack delivery records created in this range." />
        <section className="work-panel state-panel"><h2>Scoring failures</h2>
          <p className="metric-title">{number(totals.failures)} source logs</p>
          <Link className="work-link" to={logLink({ analysisStatus: "FAILED" })}>Inspect failed scoring</Link>
        </section>
      </div>
    </>}
  </div>;
}
