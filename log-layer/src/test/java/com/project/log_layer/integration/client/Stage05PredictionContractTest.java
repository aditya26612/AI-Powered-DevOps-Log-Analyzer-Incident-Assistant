package com.project.log_layer.integration.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.AnalysisStatus;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.integration.exception.MlServiceException;
import com.project.log_layer.mapper.LogMapper;
import com.project.log_layer.service.OperationalTriage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

class Stage05PredictionContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
        "INFO,COMPLETED,false,false", "ERROR,COMPLETED,false,true",
        "INFO,COMPLETED,true,true", "FATAL,COMPLETED,true,true",
        "INFO,FAILED,false,false", "ERROR,FAILED,false,true"
    })
    void eligibilityKeepsStatisticalAndOperationalFactsIndependent(
            LogLevel level, AnalysisStatus status, boolean anomaly, boolean eligible) {
        var entity = Log.builder().level(level).analysisStatus(status).anomaly(anomaly).build();
        var response = Mappers.getMapper(LogMapper.class).toLogResponse(entity);
        assertEquals(eligible, OperationalTriage.eligible(entity));
        assertEquals(eligible, response.isDiagnosisEligible());
        assertEquals(status == AnalysisStatus.COMPLETED ? anomaly : null, response.getAnomaly());
        if (status == AnalysisStatus.FAILED) {
            assertNull(response.getDecisionScore());
            assertNull(response.getModelVersion());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "'\"1\"',Normal,false,-0.02,2.0.0-candidate",
        "1,Anomaly,false,-0.02,2.0.0-candidate",
        "1,Normal,false,-0.021,2.0.0-candidate",
        "1,Normal,false,1e100,2.0.0-candidate",
        "1,Normal,false,-0.02,unexpected",
        "1,Normal,'\"false\"',-0.02,2.0.0-candidate"
    })
    void invalidTypedAndSemanticResponsesAreRejected(String prediction, String label, String anomaly, String score,
                                                        String modelVersion) throws Exception {
        var body = JSON.readTree(("""
                {"prediction":%s,"prediction_label":"%s","is_anomaly":%s,"decision_score":%s,"model_version":"%s"}
                """).formatted(prediction, label, anomaly, score, modelVersion));
        assertThrows(MlServiceException.class,
                () -> WebClientMlInferenceClient.readPrediction(body).requireConsistentResult());
    }

    @Test
    void thresholdEqualityIsNormalAndV1RollbackIsRecognized() {
        assertDoesNotThrow(() -> WebClientMlInferenceClient.readPrediction(JSON.readTree("""
                {"prediction":1,"prediction_label":"Normal","is_anomaly":false,"decision_score":-0.02,
                 "model_version":"2.0.0-candidate"}
                """)).requireConsistentResult());
        assertDoesNotThrow(() -> WebClientMlInferenceClient.readPrediction(JSON.readTree("""
                {"prediction":-1,"prediction_label":"Anomaly","is_anomaly":true,"decision_score":-0.02,
                 "model_version":"1.0.0"}
                """)).requireConsistentResult());
    }
}
