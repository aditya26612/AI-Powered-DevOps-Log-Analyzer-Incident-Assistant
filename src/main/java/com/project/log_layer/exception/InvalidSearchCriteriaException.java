package com.project.log_layer.exception;

import com.project.log_layer.enums.ErrorCode;

/**
 * Thrown when an invalid search request is received.
 *
 * <p>
 * Examples:
 * - Start time is after end time
 * - Invalid search criteria combination
 * </p>
 */
public class InvalidSearchCriteriaException extends LogException {

    public InvalidSearchCriteriaException(String message) {
        super(
                ErrorCode.INVALID_SEARCH_CRITERIA,
                message
        );
    }

}