package com.project.log_layer.enums;

/**
 * Defines the types of AI analysis that can be performed on a log.
 *
 * <p>This enum is used by {@code LogAnalysisRequest} to specify
 * which analyses should be executed by the AI layer.</p>
 *
 * <p>Using an enum instead of multiple boolean flags makes the API
 * more scalable and easier to extend with new analysis capabilities.</p>
 *
 * <p>Example:</p>
 *
 * <pre>
 * {
 *   "model": "llama3",
 *   "analysisTypes": [
 *      "SUMMARY",
 *      "ROOT_CAUSE",
 *      "SUGGESTED_FIX"
 *   ]
 * }
 * </pre>
 */
public enum AnalysisType {

    /**
     * Generate a concise summary of the log.
     */
    SUMMARY,

    /**
     * Identify the most probable root cause.
     */
    ROOT_CAUSE,

    /**
     * Suggest possible fixes for the detected issue.
     */
    SUGGESTED_FIX,

    /**
     * Recommend actions to resolve or prevent the issue.
     */
    RECOMMENDED_ACTIONS

}


// //  for future
//
//public enum AnalysisType {
//
//    SUMMARY,
//
//    ROOT_CAUSE,
//
//    SUGGESTED_FIX,
//
//    RECOMMENDED_ACTIONS,
//
//    SECURITY_ANALYSIS,
//
//    PERFORMANCE_ANALYSIS,
//
//    MEMORY_ANALYSIS,
//
//    DEPENDENCY_ANALYSIS,
//
//    COST_OPTIMIZATION,
//
//    ANOMALY_EXPLANATION
//
//}