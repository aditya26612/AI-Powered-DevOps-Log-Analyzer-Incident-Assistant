package com.project.log_layer.mapper;

import com.project.log_layer.entity.Log;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MlPredictionMapper {

    @Mapping(target = "serviceName", source = "log", qualifiedByName = "resolveServiceName")
    @Mapping(target = "message", expression = "java(log.analysisMessage())")
    @Mapping(target = "timestamp", expression = "java(log.getTimestamp().toInstant(java.time.ZoneOffset.UTC).toString())")
    MlPredictionRequest toRequest(Log log);

    @Named("resolveServiceName")
    default String resolveServiceName(Log log) {
        if (log == null) {
            return null;
        }
        if (log.getServiceName() != null && !log.getServiceName().isBlank()) {
            return log.getServiceName();
        }
        return log.getApplicationName();
    }

}
