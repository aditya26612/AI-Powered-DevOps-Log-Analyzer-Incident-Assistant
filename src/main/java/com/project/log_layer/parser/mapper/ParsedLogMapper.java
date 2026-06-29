package com.project.log_layer.parser.mapper;

import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.LogStatus;
import com.project.log_layer.parser.model.ParsedLogData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps ParsedLogData into the Log entity.
 */
@Mapper(componentModel = "spring",
        imports = LogStatus.class)
public interface ParsedLogMapper {

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "correlationId", ignore = true)

    @Mapping(target = "createdAt", ignore = true)

    @Mapping(target = "level", source = "logLevel")   // <-- ADD THIS

    @Mapping(target = "status",
            expression = "java(LogStatus.RECEIVED)")

    @Mapping(target = "anomaly",
            constant = "false")

    @Mapping(target = "anomalyScore",
            constant = "0.0")

    Log toEntity(ParsedLogData parsedLogData);

}