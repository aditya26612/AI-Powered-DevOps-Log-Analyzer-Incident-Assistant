import api from "./axios";

export const selectionKey = "devinsight.activeOrganization";

const orgPath = (id) => `/api/v1/organizations/${encodeURIComponent(id)}`;
const apps = (org) => `${orgPath(org)}/applications`;
const appPath = (org, id) => `${apps(org)}/${encodeURIComponent(id)}`;
const keyPath = (org, app) => `${appPath(org, app)}/credentials`;

export const listOrganizations = () => api.get("/api/v1/organizations").then((r) => r.data);
export const createOrganization = (data) => api.post("/api/v1/organizations", data).then((r) => r.data);
export const listApplications = (org) => api.get(apps(org)).then((r) => r.data);
export const createApplication = (org, data) => api.post(apps(org), data).then((r) => r.data);
export const updateApplication = (org, id, data) => api.patch(appPath(org, id), data).then((r) => r.data);
export const deactivateApplication = (org, id) => api.delete(appPath(org, id));
export const listCredentials = (org, app) => api.get(keyPath(org, app)).then((r) => r.data);
export const createCredential = (org, app, data) => api.post(keyPath(org, app), data).then((r) => r.data);
export const rotateCredential = (org, app, id, data) =>
  api.post(`${keyPath(org, app)}/${encodeURIComponent(id)}/rotate`, data).then((r) => r.data);
export const revokeCredential = (org, app, id) => api.delete(`${keyPath(org, app)}/${encodeURIComponent(id)}`);
export const setSlack = (org, app, url) => api.put(`${appPath(org, app)}/slack`, { url });
