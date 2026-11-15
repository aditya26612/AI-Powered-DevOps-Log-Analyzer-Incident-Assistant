import api from "./axios";

export const getIntegrations = async () => {
  const response = await api.get("/api/v1/integrations");
  return response.data;
};

export const createIntegration = async (integrationData) => {
  const response = await api.post("/api/v1/integrations", integrationData);
  return response.data;
};

export const deleteIntegration = async (id) => {
  await api.delete(`/api/v1/integrations/${id}`);
};

export const runTestSimulation = async (testData) => {
  const response = await api.post("/api/v1/integrations/test", testData);
  return response.data;
};

