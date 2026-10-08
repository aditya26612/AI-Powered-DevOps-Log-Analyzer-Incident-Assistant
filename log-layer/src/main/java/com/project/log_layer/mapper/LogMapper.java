package com.project.log_layer.mapper;

import com.project.log_layer.dto.response.common.PagedResponse;
import com.project.log_layer.dto.response.log.LogResponse;
import com.project.log_layer.entity.Log;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Maps Log entities to response DTOs.
 *
 * <p>
 * This mapper is implemented automatically by MapStruct.
 * It converts database entities into API response objects.
 * </p>
 */
@Mapper(componentModel = "spring")
public interface LogMapper {

    /**
     * Converts a Log entity into a LogResponse.
     *
     * @param log Log entity
     * @return LogResponse
     */

    LogResponse toLogResponse(Log log);

    /**
     * Converts a list of Log entities into response DTOs.
     *
     * @param logs List of Log entities
     * @return List of LogResponse
     */
    List<LogResponse> toLogResponseList(List<Log> logs);

    /**
     * Converts a Page of Log entities into a generic paged response.
     *
     * @param page Spring Data page
     * @return Generic paged response
     */
    default PagedResponse<LogResponse> toPagedResponse(Page<Log> page) {

        return PagedResponse.<LogResponse>builder()
                .content(toLogResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .first(page.isFirst())
                .last(page.isLast())
                .numberOfElements(page.getNumberOfElements())
                .sortBy(
                        page.getSort().isSorted()
                                ? page.getSort().iterator().next().getProperty()
                                : null
                )
                .sortDirection(
                        page.getSort().isSorted()
                                ? page.getSort().iterator().next().getDirection().name()
                                : null
                )
                .build();
    }

}