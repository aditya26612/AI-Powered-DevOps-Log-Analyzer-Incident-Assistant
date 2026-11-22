import { useCallback, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { getLogPage } from "../api/observabilityApi";
import { ApplicationSelect, RangeSelect, Refresh, ResourceError, Status } from "../components/WorkspaceControls";
import { useWorkspace } from "../context/workspace";
import useResource from "../hooks/useResource";
import { boundedPage, dateTime, label, levels, rangeKey } from "../observability";

const statuses = ["PENDING","PROCESSING","COMPLETED","FAILED"];
const environments = ["DEVELOPMENT","TESTING","QA","STAGING","PRODUCTION"];
const sources = ["SPRING_BOOT","DOCKER","NGINX","KUBERNETES","APACHE","JENKINS","KAFKA","REDIS","MYSQL","SYSTEM","CUSTOM","PYTHON","NODEJS","GO"];
const uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

function SearchFilters({ initial, onSearch }) {
  const [draft, setDraft] = useState(initial);
  function change(event) {
    setDraft({ ...draft, [event.target.name]: event.target.value });
  }
  return <form className="work-search" onSubmit={(event) => {
    event.preventDefault();
    onSearch(draft);
  }}>
    <label className="work-field">Search message
      <input name="search" type="search" value={draft.search} onChange={change} minLength={2} maxLength={100} placeholder="Find text in messages" />
    </label>
    <label className="work-field">Trace ID
      <input name="trace" value={draft.trace} onChange={change} pattern="[0-9a-f]{32}" maxLength={32} placeholder="32 lowercase hex digits" />
    </label>
    <label className="work-field">Correlation ID
      <input name="correlation" value={draft.correlation} onChange={change} pattern={uuid} maxLength={36} placeholder="Correlation UUID" />
    </label>
    <button type="submit" className="work-button"><Search size={16} aria-hidden="true" /> Apply search</button>
  </form>;
}

function SelectFilter({ title, name, values, filters, change }) {
  return <label className="work-field">{title}
    <select value={filters.get(name) ?? ""} onChange={(event) => change({ [name]: event.target.value })}>
      <option value="">Any</option>
      {values.map((value) => <option value={value} key={value}>{label(value)}</option>)}
    </select>
  </label>;
}

export default function LogExplorer() {
  const { organization, applications, applicationError } = useWorkspace();
  const [filters, setFilters] = useSearchParams();
  const [messageSearch, setMessageSearch] = useState("");
  const range = rangeKey(filters.get("range"));
  const application = filters.get("application") ?? "";
  const page = boundedPage(filters.get("page"));
  const size = filters.get("size") === "50" ? 50 : 25;
  const searchValues = { search: messageSearch, trace: filters.get("trace") ?? "",
    correlation: filters.get("correlation") ?? "" };
  const query = filters.toString();
  const org = organization.id;
  const load = useCallback((signal) => {
    const params = new URLSearchParams(query);
    return getLogPage(org, {
      range, applicationId: application || undefined, page, size, search: messageSearch || undefined,
      before: params.get("before") || undefined,
      ...Object.fromEntries(["level","source","environment","analysisStatus","classification","operationalErrors",
        "incident","trace","correlation"].filter((key) => params.get(key)).map((key) => [key, params.get(key)])),
    }, signal);
  }, [org, query, range, application, page, size, messageSearch]);
  const resource = useResource(JSON.stringify([org,query,messageSearch]), load);
  const data = resource.data;

  function change(update, paging = false) {
    setFilters((current) => {
      const next = new URLSearchParams(current);
      if (!paging) {
        next.delete("page");
        next.delete("before");
      }
      Object.entries(update).forEach(([key,value]) => {
        if (String(value).trim()) next.set(key, String(value).trim());
        else next.delete(key);
      });
      return next;
    });
  }
  function navigatePage(next) {
    change({ page: next, before: data.to }, true);
  }
  function refresh() {
    if (page || filters.has("before")) change({});
    else resource.refresh();
  }

  return <section className="work-panel log-explorer" aria-label="Log explorer">
    <div className="work-toolbar">
      <RangeSelect value={range} onChange={(value) => change({ range: value })} />
      <ApplicationSelect applications={applications} value={application} onChange={(value) => change({ application: value })} />
      <Refresh resource={{ ...resource, refresh }} />
    </div>
    {applicationError && <p role="alert" className="work-alert">The application filter could not load.</p>}
    <div className="work-filters">
      <SelectFilter title="Severity" name="level" values={levels} filters={filters} change={change} />
      <SelectFilter title="Source" name="source" values={sources} filters={filters} change={change} />
      <SelectFilter title="Environment" name="environment" values={environments} filters={filters} change={change} />
      <SelectFilter title="ML status" name="analysisStatus" values={statuses} filters={filters} change={change} />
      <SelectFilter title="Classification" name="classification" values={["V2_ANOMALY","V2_NORMAL"]} filters={filters} change={change} />
    </div>
    <SearchFilters key={JSON.stringify(searchValues)} initial={searchValues} onSearch={({ search, ...values }) => {
      setMessageSearch(search.trim());
      change(values);
    }} />
    <div className="work-filter-tags">
      {filters.has("operationalErrors") && <button className="work-button" onClick={() => change({ operationalErrors: "" })}>ERROR or FATAL ×</button>}
      {filters.has("incident") && <button className="work-button" onClick={() => change({ incident: "" })}>Incident filter ×</button>}
      {(filters.size > 0 || messageSearch) && <button className="work-text-button" onClick={() => {
        setMessageSearch("");
        setFilters({});
      }}>Clear filters</button>}
    </div>
    <ResourceError resource={resource} />
    {!data && !resource.error && <p className="work-loading" role="status">Loading logs…</p>}
    {data && (data.content.length ? <div className="work-table-scroll" tabIndex={0} role="region" aria-label="Persisted logs">
      <table className="work-table log-table">
        <thead><tr>{["Occurrence","Application","Severity","Source","Message","V2 classification","ML status","Incident"].map((title) =>
          <th key={title} scope="col">{title}</th>)}</tr></thead>
        <tbody>{data.content.map((row) => <tr key={row.id}>
          <td><time dateTime={row.occurrence}>{dateTime(row.occurrence)}</time></td>
          <td>{row.application}<small>{label(row.environment)}</small></td>
          <td><Status value={row.level} /></td><td>{label(row.source)}</td>
          <td><Link className="work-link" to={`/logs/${row.id}/analyze`}>{row.preview || `Log ${row.id}`}</Link></td>
          <td>{row.classification ? <Status value={row.classification} /> : "Unavailable"}</td>
          <td><Status value={row.analysisStatus} /></td>
          <td>{row.incidentId ? <Link className="work-link" to={`/incidents?incident=${row.incidentId}`}>View incident</Link> : "—"}</td>
        </tr>)}</tbody>
      </table>
    </div> : <p className="work-empty">No persisted logs match these filters.</p>)}
    <div className="work-paging">
      <label className="work-field">Rows per page
        <select value={size} onChange={(event) => change({ size: event.target.value })}>
          <option value="25">25</option><option value="50">50</option>
        </select>
      </label>
      <div><button type="button" className="work-button" disabled={!data || page === 0} onClick={() => navigatePage(page - 1)}>Previous</button>
        <span>Page {page + 1}</span>
        <button type="button" className="work-button" disabled={!data?.hasNext} onClick={() => navigatePage(page + 1)}>Next</button></div>
    </div>
  </section>;
}
