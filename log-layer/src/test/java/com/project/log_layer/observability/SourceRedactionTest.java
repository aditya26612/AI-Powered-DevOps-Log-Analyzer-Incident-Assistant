package com.project.log_layer.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceRedactionTest {
    private static final String PRIVATE_KEY_TYPE = "TEST PRIVATE KEY";
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN " + PRIVATE_KEY_TYPE + "-----";
    private static final String PRIVATE_KEY_END = "-----END " + PRIVATE_KEY_TYPE + "-----";

    @ParameterizedTest
    @ValueSource(strings = {
            "password=synthetic-password",
            "password\": \"synthetic credential\"",
            "Bearer synthetic-session-marker",
            "eyJfixtureHeader.fixturePayload.syntheticSignature",
            "jdbc:postgresql://fixture:synthetic@localhost/fixture",
            PRIVATE_KEY_BEGIN + "\nsynthetic-key-material\n" + PRIVATE_KEY_END
    })
    void masksCredentialShapesWithoutChangingAdjacentLogLines(String credential) {
        assertEquals("before\n[redacted]\nafter", ObservabilityQueries.redact("before\n" + credential + "\nafter"));
    }

    @Test
    void masksPrivateKeyMaterialInTruncatedPreviews() {
        assertEquals("before\n[redacted]", ObservabilityQueries.redact(
                "before\n" + PRIVATE_KEY_BEGIN + "\ntruncated-fixture-key"));
    }
}
