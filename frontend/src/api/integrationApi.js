import api from "./axios";

const connectionPath = (org) => `${orgPath(org)}/loki-connection`;

export const getLokiConnection = (org, signal) => api.get(connectionPath(org), { signal }).then((r) => r.data);
export const saveLokiConnection = (org, endpoint) => api.put(connectionPath(org), { endpoint }).then((r) => r.data);

const sourcePath = (org, app) => `${appPath(org, app)}/log-source`;

export const getLogSource = (org, app, signal) => api.get(sourcePath(org, app), { signal }).then((r) => r.data);
export const saveLogSource = (org, app, settings) => api.put(sourcePath(org, app), settings).then((r) => r.data);
export const replayLogSource = (org, app) => api.post(`${sourcePath(org, app)}/replay`).then((r) => r.data);

export const selectionKey = "devinsight.activeOrganization";

const orgPath = (id) => `/api/v1/organizations/${encodeURIComponent(id)}`;
const apps = (org) => `${orgPath(org)}/applications`;
const appPath = (org, id) => `${apps(org)}/${encodeURIComponent(id)}`;
const keyPath = (org, app) => `${appPath(org, app)}/credentials`;

export const listOrganizations = (signal) => api.get("/api/v1/organizations", { signal }).then((r) => r.data);
export const createOrganization = (data) => api.post("/api/v1/organizations", data).then((r) => r.data);
export const listApplications = (org, signal) => api.get(apps(org), { signal }).then((r) => r.data);
export const createApplication = (org, data) => api.post(apps(org), data).then((r) => r.data);
export const updateApplication = (org, id, data) => api.patch(appPath(org, id), data).then((r) => r.data);
export const deactivateApplication = (org, id) => api.delete(appPath(org, id));
export const listCredentials = (org, app) => api.get(keyPath(org, app)).then((r) => r.data);
export const createCredential = (org, app, data) => api.post(keyPath(org, app), data).then((r) => r.data);
export const rotateCredential = (org, app, id, data) =>
  api.post(`${keyPath(org, app)}/${encodeURIComponent(id)}/rotate`, data).then((r) => r.data);
export const revokeCredential = (org, app, id) => api.delete(`${keyPath(org, app)}/${encodeURIComponent(id)}`);
export const setSlack = (org, app, url) => api.put(`${appPath(org, app)}/slack`, { url });
export const setSlackPolicy = (org, app, enabled, minimumSeverity) =>
  api.put(`${appPath(org, app)}/slack/policy`, { enabled, minimumSeverity });
