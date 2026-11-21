package com.project.log_layer.dto.request.ingest;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {
    @Test
    void validatesW3cContextAndDefaultsToReportedProvenance() {
        var trace = TraceContext.parse("00-268ced385f22156b8c8233406b31c3da-38f598163b30a21c-01",null);
        assertEquals("268ced385f22156b8c8233406b31c3da",trace.traceId());
        assertEquals(TraceContext.Origin.COLLECTOR_REPORTED,trace.origin());
        assertNull(TraceContext.parse(null,null));
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(null,TraceContext.Origin.INBOUND_REPORTED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00-00000000000000000000000000000000-38f598163b30a21c-01",
            "00-268ced385f22156b8c8233406b31c3da-0000000000000000-01",
            "ff-268ced385f22156b8c8233406b31c3da-38f598163b30a21c-01",
            "00-268ced385f22156b8c8233406b31c3da-38f598163b30a2-01"})
    void rejectsInvalidTraceparent(String parent) {
        assertThrows(IllegalArgumentException.class, () -> TraceContext.parse(parent,null));
    }

    @Test
    void absentTraceLeavesRetainedCanonicalBytesUnchanged() throws Exception {
        String v1 = """
                {"eventId":"ef40abbe-e882-4974-8713-69674b9a1234","timestamp":"2026-11-21T10:00:00Z","level":"INFO","source":"PYTHON","message":"old","exception":null,"loggerName":null,"threadName":null,"hostName":null,"correlationId":null,"metadata":{}}
                """.strip();
        var json = JsonMapper.builder().build();
        assertEquals(v1, json.writeValueAsString(json.readValue(v1, ExternalLogEvent.class)));
    }
}
