package com.project.log_layer.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response returned after processing a batch operation.
 *
 * <p>
 * Used for bulk log ingestion and future bulk operations.
 * Provides an execution summary instead of returning
 * every processed record.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResponse {

    /**
     * Timestamp when the batch operation completed.
     */
    private LocalDateTime timestamp;

    /**
     * Total number of records received.
     */
    private Integer totalRecords;

    /**
     * Number of successfully processed records.
     */
    private Integer successfulRecords;

    /**
     * Number of failed records.
     */
    private Integer failedRecords;

    /**
     * Indicates whether the entire batch
     * completed successfully.
     */
    private Boolean success;

    /**
     * Human-readable summary.
     *
     * Example:
     * "95 of 100 logs processed successfully."
     */
    private String message;

    /**
     * Optional list of processing errors.
     *
     * Populated only when failures occur.
     */
    private List<String> errors;

}