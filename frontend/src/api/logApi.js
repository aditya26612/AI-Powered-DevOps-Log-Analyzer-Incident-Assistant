import api from "./axios";

export const ingestLog = async (logData) => {
  const response = await api.post("/api/v1/logs", logData);
  return response.data;
};

export const getLogById = async (id) => {
  const response = await api.get(`/api/v1/logs/${id}`);
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