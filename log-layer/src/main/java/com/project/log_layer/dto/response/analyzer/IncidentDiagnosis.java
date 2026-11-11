package com.project.log_layer.dto.response.analyzer;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDiagnosis {

    private String summary;
    private String probableRootCause;
    private String severity;
    private String suggestedFix;
    private List<String> remediationCommands;
    private List<String> relevantKnowledgeDocs;

}

