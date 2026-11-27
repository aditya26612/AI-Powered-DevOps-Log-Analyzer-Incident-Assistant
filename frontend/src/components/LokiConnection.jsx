import { useCallback, useState } from "react";
import { getLokiConnection, saveLokiConnection } from "../api/integrationApi";
import useResource, { readError } from "../hooks/useResource";

function ConnectionForm({ organization, current, refresh }) {
  const [endpoint, setEndpoint] = useState(current ?? "");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const saved = await saveLokiConnection(organization, endpoint.trim());
      setEndpoint(saved.endpoint);
      setNotice("Loki endpoint saved.");
      refresh();
    } catch (failure) {
      setError(failure.response?.status === 400
        ? "Enter an approved Loki base URL for this organization."
        : readError(failure));
    } finally {
      setBusy(false);
    }
  }

  return <>
    <form onSubmit={save}>
      <label>Loki endpoint <input type="url" required maxLength={512} value={endpoint}
        placeholder="https://loki.example.com" disabled={busy} onChange={(event) => {
          setEndpoint(event.target.value);
          setNotice("");
          setError("");
        }} /></label>
      <button type="submit" disabled={busy}>{busy ? "Saving…" : "Save Loki endpoint"}</button>
    </form>
    {notice && <p role="status">{notice}</p>}
    {error && <p role="alert">{error}</p>}
  </>;
}

export default function LokiConnection({ organization }) {
  const load = useCallback((signal) => getLokiConnection(organization, signal), [organization]);
  const { data, error, refresh } = useResource(organization, load);

  return <section className="tenant-panel" aria-label="Organization Loki connection">
    <h2>Organization Loki connection</h2>
    {error && <p role="alert">{error} <button type="button" onClick={refresh}>Retry</button></p>}
    {!data && !error && <p role="status">Loading Loki connection…</p>}
    {data && (data.canConfigure ? <>
      <p>Enter an operator-approved base URL. Choose the stream separately in each application's settings.</p>
      <ConnectionForm organization={organization} current={data.endpoint} refresh={refresh} />
    </> : <p>A DevInsight operator must approve a Loki endpoint for this organization.</p>)}
  </section>;
}
