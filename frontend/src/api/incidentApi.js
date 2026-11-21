import api from "./axios";

const incidentPath = (org, id = "") =>
  `/api/v1/organizations/${encodeURIComponent(org)}/incidents${id ? `/${encodeURIComponent(id)}` : ""}`;

export const listIncidents = (org, params, signal) =>
  api.get(incidentPath(org), { params, signal }).then((response) => response.data);

export const incidentDetail = (org, id, signal) =>
  api.get(incidentPath(org, id), { signal }).then((response) => response.data);

export const listIncidentLogs = (org, id, page, signal) =>
  api.get(`${incidentPath(org, id)}/logs`, { params: { page }, signal }).then((response) => response.data);

export const changeIncidentStatus = (org, id, action, version) =>
  api.post(`${incidentPath(org, id)}/${action}`, { version }).then((response) => response.data);

export const incidentDiagnosis = (org, id, signal) =>
  api.get(`${incidentPath(org, id)}/diagnosis`, { signal }).then((response) => response.data);

export const retryIncidentDiagnosis = (org, id) =>
  api.post(`${incidentPath(org, id)}/diagnosis/retry`).then((response) => response.data);
