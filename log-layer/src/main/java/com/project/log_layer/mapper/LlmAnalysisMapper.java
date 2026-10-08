package com.project.log_layer.mapper;

import com.project.log_layer.entity.Log;
import com.project.log_layer.integration.dto.LlmAnalysisRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LlmAnalysisMapper {

    @Mapping(source = "applicationName", target = "serviceName")
    LlmAnalysisRequest toRequest(Log log);

}