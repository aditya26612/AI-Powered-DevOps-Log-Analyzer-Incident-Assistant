package com.project.log_layer.specification;

import com.project.log_layer.dto.request.search.LogFilterRequest;
import com.project.log_layer.entity.Log;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Builds a dynamic Specification for Log searches.
 *
 * <p>
 * Combines individual Specifications based on the supplied
 * LogFilterRequest.
 * </p>
 */
@Component
public class LogSpecificationBuilder {

    /**
     * Builds a combined Specification for the supplied request.
     *
     * @param request search request
     * @return combined specification
     */
    public Specification<Log> build(LogFilterRequest request) {

        Specification<Log> specification = Specification.unrestricted();

        specification = specification.and(
                LogSpecification.hasLevel(request.getLevel())
        );

        specification = specification.and(
                LogSpecification.hasSource(request.getSource())
        );

        specification = specification.and(
                LogSpecification.hasEnvironment(request.getEnvironment())
        );

        specification = specification.and(
                LogSpecification.hasStatus(request.getStatus())
        );

        specification = specification.and(
                LogSpecification.hasAnomaly(request.getAnomaly())
        );

        specification = specification.and(
                LogSpecification.hasApplicationName(request.getApplicationName())
        );

        specification = specification.and(
                LogSpecification.hasServiceName(request.getServiceName())
        );

        specification = specification.and(
                LogSpecification.hasLoggerName(request.getLoggerName())
        );

        specification = specification.and(
                LogSpecification.hasThreadName(request.getThreadName())
        );

        specification = specification.and(
                LogSpecification.hasHostName(request.getHostName())
        );

        specification = specification.and(
                LogSpecification.hasMessage(request.getMessage())
        );

        specification = specification.and(
                LogSpecification.hasCorrelationId(request.getCorrelationId())
        );

        specification = specification.and(
                LogSpecification.timestampBetween(
                        request.getStartTime(),
                        request.getEndTime()
                )
        );

        return specification;
    }

}