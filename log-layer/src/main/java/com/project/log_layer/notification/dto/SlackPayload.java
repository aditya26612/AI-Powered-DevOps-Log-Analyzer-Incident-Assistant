package com.project.log_layer.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload structure for Slack Incoming Webhooks with Block Kit formatting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlackPayload {

    private String text;
    private String channel;
    private String username;

    @JsonProperty("icon_emoji")
    private String iconEmoji;

    private List<Block> blocks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Block {
        private String type;
        private TextObject text;
        private List<TextObject> fields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextObject {
        private String type; // "plain_text" or "mrkdwn"
        private String text;
        private Boolean emoji;
    }
}

