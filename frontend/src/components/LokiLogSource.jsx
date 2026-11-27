import { useCallback, useState } from "react";
import { getLogSource, replayLogSource, saveLogSource } from "../api/integrationApi";
import useResource, { readError } from "../hooks/useResource";

function SourceForm({ status, save, busy }) {
  const [labelName, setLabelName] = useState(status.labelName ?? "app");
  const [labelValue, setLabelValue] = useState(status.labelValue ?? "");
  const [enabled, setEnabled] = useState(status.labelName ? status.enabled : true);

  return <form onSubmit={(event) => {
    event.preventDefault();
    save({ labelName, labelValue, enabled });
  }}>
    <label>Stream label <input value={labelName} required pattern="[a-zA-Z_][a-zA-Z0-9_]*"
      maxLength={128} onChange={(event) => setLabelName(event.target.value)} /></label>
    <label>Label value <input value={labelValue} required maxLength={200} placeholder="pull-demo"
      onChange={(event) => setLabelValue(event.target.value)} /></label>
    <label>Automatic sync <input type="checkbox" checked={enabled}
      onChange={(event) => setEnabled(event.target.checked)} /></label>
    <button type="submit" disabled={busy}>Save log source</button>
  </form>;
}

export default function LokiLogSource({ organization, application, admin }) {
  const load = useCallback((signal) => getLogSource(organization, application, signal), [organization, application]);
  const { data: status, error, refresh, refreshing } = useResource(organization + "/" + application, load, 5000);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  async function perform(action, success) {
    setBusy(true);
    setMessage("");
    setFailure("");
    try {
      await action();
      setMessage(success);
      refresh();
    } catch (error) {
      setFailure(error.response?.status === 400
        ? "Use a valid Loki label name and an exact label value."
        : readError(error));
    } finally {
      setBusy(false);
    }
  }

  return <section className="work-details" aria-label="Grafana Loki log source">
    <h3>Grafana Loki log source</h3>
    {error && <p role="alert">{error} <button type="button" disabled={refreshing} onClick={refresh}>Retry</button></p>}
    {!status && !error && <p role="status">Loading log source…</p>}
    {status && <>
      {!status.available && <p>Ask the organization owner to save an approved Loki endpoint.</p>}
      {status.labelName && <dl>
        <dt>Stream selector</dt><dd><code>{`{${status.labelName}="${status.labelValue}"}`}</code></dd>
        <dt>Sync status</dt><dd>{!status.available ? "Unavailable" : !status.enabled ? "Paused"
          : status.lastError ? "Retrying" : status.lastSuccessAt ? "Synced" : "Waiting for first sync"}</dd>
        <dt>Last successful sync</dt><dd>{status.lastSuccessAt ? new Date(status.lastSuccessAt).toLocaleString() : "Pending"}</dd>
        <dt>Logs imported</dt><dd>{status.importedCount}</dd>
        <dt>Duplicates skipped</dt><dd>{status.duplicateCount}</dd>
      </dl>}
      {status.lastError && status.enabled && <p role="alert">{status.lastError}</p>}
      {admin && status.available && <SourceForm key={JSON.stringify([status.labelName, status.labelValue, status.enabled])}
        status={status} busy={busy} save={(settings) => perform(
          () => saveLogSource(organization, application, settings), "Log source saved."
        )} />}
      {admin && status.canReplay && <button type="button" disabled={busy} onClick={() => perform(
        () => replayLogSource(organization, application), "Replay scheduled for the next sync."
      )}>Replay last import</button>}
    </>}
    {message && <p role="status">{message}</p>}
    {failure && <p role="alert">{failure}</p>}
  </section>;
}
