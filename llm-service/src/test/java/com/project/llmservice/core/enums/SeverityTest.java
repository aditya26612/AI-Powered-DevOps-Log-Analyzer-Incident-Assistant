package com.project.llmservice.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeverityTest {

    @Test
    void shouldContainAllSeverityLevels() {

        assertEquals(
                4,
                Severity.values().length
        );

        assertEquals(Severity.LOW, Severity.valueOf("LOW"));
        assertEquals(Severity.MEDIUM, Severity.valueOf("MEDIUM"));
        assertEquals(Severity.HIGH, Severity.valueOf("HIGH"));
        assertEquals(Severity.CRITICAL, Severity.valueOf("CRITICAL"));
    }
}