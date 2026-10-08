package com.project.llmservice.prompt;

public enum PromptType {

    ROOT_CAUSE_ANALYSIS("prompts/v1/root-cause-analysis.prompt");

    private final String resourcePath;

    PromptType(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }
}