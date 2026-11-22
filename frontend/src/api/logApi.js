import api from "./axios";

export const ingestLog = async (logData) => {
  const response = await api.post("/api/v1/logs", logData);
  return response.data;
};

export const getLogById = async (id, organization, signal) => {
  const response = await api.get(`/api/v1/logs/${encodeURIComponent(id)}`, {
    headers: organization ? { "X-DevInsight-Organization": organization } : undefined, signal,
  });
  return response.data;
};

export const searchLogs = async (filterData) => {
  const response = await api.post("/api/v1/logs/search", filterData);
  return response.data;
};

export const analyzeLog = async (id, analysisData) => {
  const response = await api.post(
    `/api/v1/logs/${id}/analyze`,
    analysisData
  );
  return response.data;
};

export const deleteLog = async (id) => {
  await api.delete(`/api/v1/logs/${id}`);
};

export const inspectLog = async (inspectData) => {
  const response = await api.post("/api/v1/analyzer/inspect", inspectData);
  return response.data;
};
