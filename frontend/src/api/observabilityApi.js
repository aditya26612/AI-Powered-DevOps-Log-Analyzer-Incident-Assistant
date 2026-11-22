import api from "./axios";

const base = (organization) => `/api/v1/organizations/${encodeURIComponent(organization)}/observability`;

export const getSummary = (organization, params, signal) =>
  api.get(`${base(organization)}/summary`, { params, signal, timeout: 12000 }).then(({ data }) => data);

export const getLogPage = (organization, { search, ...params }, signal) => {
  const url = `${base(organization)}/logs`;
  const config = { params, signal, timeout: 12000 };
  return (search ? api.post(url, { text: search }, config) : api.get(url, config)).then(({ data }) => data);
};

export const getLogSource = (organization, id, signal) =>
  api.get(`${base(organization)}/logs/${encodeURIComponent(id)}/source`, { signal, timeout: 12000 }).then(({ data }) => data);
