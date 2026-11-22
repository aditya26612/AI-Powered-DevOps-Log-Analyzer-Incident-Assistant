import { useCallback, useState } from "react";
import api from "../api/axios";
import { ResourceError, Status } from "../components/WorkspaceControls";
import useResource, { readError } from "../hooks/useResource";
import { dateTime, label } from "../observability";

const labels = {
  PENDING: "Queued", SENDING: "Sending", RETRY_WAIT: "Retry scheduled", DELIVERED: "Delivered",
  FAILED_TERMINAL: "Failed", DELIVERY_UNKNOWN: "Delivery unknown", CANCELLED: "Cancelled", SKIPPED: "Skipped",
};

export default function IncidentNotifications({ organization, incident, canRetry }) {
  const [deliveryPage, setDeliveryPage] = useState(0);
  const [selection, setSelection] = useState("");
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const path = "/api/v1/organizations/" + encodeURIComponent(organization);

  const loadDeliveries = useCallback((signal) => api.get(
    path + "/incidents/" + encodeURIComponent(incident) + "/notifications",
    { params: { page: deliveryPage }, signal, timeout: 12000 }).then(({ data }) => data), [path, incident, deliveryPage]);
  const loadAttempts = useCallback((signal) => api.get(path + "/notifications/" + encodeURIComponent(selection) + "/attempts",
    { params: { page }, signal, timeout: 12000 }).then(({ data }) => data), [path, selection, page]);
  const deliveries = useResource(`${path}/${incident}/${deliveryPage}`, loadDeliveries, 8000);
  const history = useResource(selection && `${path}/${selection}/${page}`, loadAttempts, 8000);
  const attempts = history.data;

  async function retry(delivery) {
    const unknown = delivery.operatorReviewRequired;
    if (unknown && !window.confirm("Slack may already have accepted this message. Retry anyway?")) return;
    setBusy(true);
    setError("");
    try {
      await api.post(path + "/notifications/" + encodeURIComponent(delivery.id) + "/retry",
        { confirmPossibleDuplicate: unknown });
    } catch (failure) {
      setError(readError(failure));
    } finally {
      setBusy(false);
      deliveries.refresh();
      history.refresh();
    }
  }

  return <section className="work-panel incident-section" aria-label="Slack notification">
    <h3>Slack notification</h3>
    <ResourceError resource={deliveries} />
    {error && <p className="work-alert" role="alert">{error}</p>}
    {!deliveries.data && !deliveries.error && <p role="status">Loading notification status…</p>}
    {deliveries.data?.content.length === 0 && <p>No notification was scheduled.</p>}
    {deliveries.data?.content.map((delivery) => <div key={delivery.id} className="delivery-item">
      <p><Status value={delivery.status}>{labels[delivery.status]}</Status>{delivery.destinationLabel && " · " + delivery.destinationLabel}
        {" · "}{delivery.attemptCount} of {delivery.maxAttempts} attempts</p>
      <p className="work-muted">Created {dateTime(delivery.createdAt)}{delivery.lastAttemptAt && ` · Last attempt ${dateTime(delivery.lastAttemptAt)}`}</p>
      {delivery.failureCategory && <p>{label(delivery.failureCategory)}</p>}
      {delivery.operatorReviewRequired && <p className="work-muted">Slack may already have accepted this message. Retrying can send a duplicate.</p>}
      {delivery.acknowledgedAt && <p>Provider acknowledgement {dateTime(delivery.acknowledgedAt)}</p>}
      {delivery.nextAttemptAt && <p>Next retry {dateTime(delivery.nextAttemptAt)}</p>}
      <button type="button" className="work-button" aria-expanded={selection === delivery.id} onClick={() => {
        setPage(0);
        setSelection(selection === delivery.id ? "" : delivery.id);
      }}>Attempt history</button>
      {canRetry && delivery.manualRetries < 2 && ["FAILED_TERMINAL", "DELIVERY_UNKNOWN"].includes(delivery.status)
        && <button type="button" className="work-button" disabled={busy} onClick={() => retry(delivery)}>Retry Slack notification</button>}
      {selection === delivery.id && <div>
        <ResourceError resource={history} />
        {!attempts && !history.error && <p role="status">Loading attempts…</p>}
        {attempts?.content.length === 0 && <p>No attempts recorded yet.</p>}
        {attempts?.content.length > 0 && <ul>{attempts.content.map((attempt) => <li key={attempt.number}>
          Attempt {attempt.number} · {labels[attempt.outcome] || attempt.outcome || "Sending"} · {dateTime(attempt.startedAt)}
          {attempt.httpStatus && " · HTTP " + attempt.httpStatus}
          {attempt.finishedAt && ` · Finished ${dateTime(attempt.finishedAt)}`}
          {attempt.failureCategory && " · " + label(attempt.failureCategory)}
        </li>)}</ul>}
        {page > 0 && <button className="work-button" type="button" onClick={() => setPage(page - 1)}>Previous attempts</button>}
        {attempts?.hasMore && <button className="work-button" type="button" onClick={() => setPage(page + 1)}>More attempts</button>}
      </div>}
    </div>)}
    {deliveryPage > 0 && <button className="work-button" onClick={() => setDeliveryPage(deliveryPage - 1)}>Previous deliveries</button>}
    {deliveries.data?.hasMore && <button className="work-button" onClick={() => setDeliveryPage(deliveryPage + 1)}>More deliveries</button>}
  </section>;
}
