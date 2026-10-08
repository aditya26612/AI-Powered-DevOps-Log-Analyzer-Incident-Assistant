package com.project.llmservice.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisStatusTest {

    @Test
    void shouldContainExpectedStatuses() {

        assertEquals(
                2,
                AnalysisStatus.values().length
        );

        assertEquals(
                AnalysisStatus.SUCCESS,
                AnalysisStatus.valueOf("SUCCESS")
        );

        assertEquals(
                AnalysisStatus.FAILED,
                AnalysisStatus.valueOf("FAILED")
        );
    }
}