package com.project.log_layer.dto.response.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Generic paginated response DTO.
 *
 * <p>This class is reusable across all modules and services
 * requiring pagination.</p>
 *
 * @param <T> Response object type.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    /**
     * List of response objects.
     */
    private List<T> content;

    /**
     * Current page number (0-based).
     */
    private Integer page;

    /**
     * Number of records requested per page.
     */
    private Integer size;

    /**
     * Total number of pages.
     */
    private Integer totalPages;

    /**
     * Total number of records.
     */
    private Long totalElements;

    /**
     * Indicates whether this is the first page.
     */
    private Boolean first;

    /**
     * Indicates whether this is the last page.
     */
    private Boolean last;

    /**
     * Number of records in the current page.
     */
    private Integer numberOfElements;

    /**
     * Sorting field.
     */
    private String sortBy;

    /**
     * Sorting direction (ASC/DESC).
     */
    private String sortDirection;

}