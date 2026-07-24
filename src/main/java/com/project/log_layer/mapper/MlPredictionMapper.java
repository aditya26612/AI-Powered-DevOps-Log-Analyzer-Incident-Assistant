package com.project.log_layer.mapper;

import com.project.log_layer.entity.Log;
import com.project.log_layer.integration.dto.MlPredictionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MlPredictionMapper {

    @Mapping(source = "applicationName", target = "serviceName")
    MlPredictionRequest toRequest(Log log);

}