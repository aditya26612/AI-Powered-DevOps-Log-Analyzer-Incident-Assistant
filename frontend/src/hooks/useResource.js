import { useEffect, useState } from "react";

export function readError(error) {
  switch (error.response?.status) {
    case 401: return "Your session expired. Sign in again.";
    case 403: return "You do not have permission to access this data.";
    case 404: return "This resource is unavailable in the selected organization.";
    case 409: return "The resource changed. Refresh and try again.";
    case 400: return "The selected filters are invalid.";
    default: return "Could not refresh data. Check the connection and try again.";
  }
}

export default function useResource(key, load, interval = 0, shouldPoll) {
  const [snapshot, setSnapshot] = useState(null);
  const [revision, setRevision] = useState(0);
  const value = snapshot?.key === key ? snapshot : null;
  useEffect(() => {
    if (!key) return;
    let controller;
    let timeout;
    let disposed = false;
    let needsRequest = true;
    async function refresh() {
      if (disposed || controller || document.hidden) return;
      const current = new AbortController();
      controller = current;
      let poll = Boolean(interval);
      try {
        const data = await load(current.signal);
        if (!current.signal.aborted) {
          needsRequest = false;
          setSnapshot({ key, data, updatedAt: Date.now(), error: "" });
          if (shouldPoll) poll = poll && shouldPoll(data);
        }
      } catch (error) {
        if (!current.signal.aborted) {
          const status = error.response?.status;
          const retain = !status || status >= 500 || status === 429;
          if (!retain) poll = false;
          setSnapshot((previous) => ({
            key, data: retain && previous?.key === key ? previous.data : null,
            updatedAt: retain && previous?.key === key ? previous.updatedAt : null, error: readError(error),
          }));
        }
      } finally {
        controller = null;
        if (!disposed && poll && !document.hidden) timeout = setTimeout(refresh, interval);
      }
    }
    function visibility() {
      clearTimeout(timeout);
      if (!document.hidden && (interval || needsRequest)) refresh();
    }
    document.addEventListener("visibilitychange", visibility);
    refresh();
    return () => {
      disposed = true;
      controller?.abort();
      clearTimeout(timeout);
      document.removeEventListener("visibilitychange", visibility);
    };
  }, [key, load, interval, revision, shouldPoll]);
  return { data: value?.data, error: value?.error, updatedAt: value?.updatedAt,
    refresh: () => setRevision((count) => count + 1) };
}
