package com.project.log_layer.logsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.log_layer.enums.Environment;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.tenant.ApplicationCredentials.Binding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LokiPollerTest {
    private final UUID org = UUID.randomUUID();
    private final URI endpoint = URI.create("http://loki:3100");
    private final LokiSources.Poll poll = new LokiSources.Poll(endpoint, new Binding(org, 1, "pull-demo",
            Environment.DEVELOPMENT), "app", "pull-demo", LokiSources.nowNanos() - 90 * LokiSources.SECOND, 0);

    @Test
    void narrowsFullWindowBeforeAdvancingCheckpoint() {
        LokiClient client = mock();
        LokiSources sources = mock();
        var line = new LokiClient.Line(poll.cursor(), Map.of("app", "pull-demo"), "hello");
        when(client.query(eq(endpoint), eq("app"), eq("pull-demo"), eq(poll.cursor()), anyLong(), eq(11)))
                .thenReturn(Collections.nCopies(11, line), List.of(line));

        new LokiPoller(client, sources).sync(poll);

        var ends = ArgumentCaptor.forClass(Long.class);
        verify(client, times(2)).query(eq(endpoint), eq("app"), eq("pull-demo"), eq(poll.cursor()), ends.capture(), eq(11));
        assertThat(ends.getAllValues().get(1)).isLessThan(ends.getAllValues().getFirst());
        verify(sources).importWindow(poll, ends.getValue(), true, List.of(line));
    }

    @Test
    void reportsSaturatedTimestampWithoutAdvancingCheckpoint() {
        LokiClient client = mock();
        LokiSources sources = mock();
        var line = new LokiClient.Line(poll.cursor(), Map.of("app", "pull-demo"), "hello");
        when(client.query(eq(endpoint), anyString(), anyString(), anyLong(), anyLong(), anyInt()))
                .thenReturn(Collections.nCopies(11, line));

        new LokiPoller(client, sources).sync(poll);

        verify(sources).fail(eq(poll), contains("too dense"));
        verifyNoMoreInteractions(sources);
    }

    @Test
    void usesStableSourceScopedIdsAndPreservesLevel() {
        var client = new LokiClient(new ObjectMapper(), "http://loki:3100", org.toString(), "", "", "");
        var labels = new TreeMap<>(Map.of("app", "pull-demo", "instance", "checkout"));
        var line = new LokiClient.Line(poll.cursor(), labels, "{\"level\":\"error\",\"message\":\"timeout\"}");
        var events = client.events(endpoint, List.of(line, line), org, 1);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().level()).isEqualTo(LogLevel.ERROR);
        assertThat(client.events(endpoint, List.of(line), org, 1)).isEqualTo(events);
        assertThat(client.events(endpoint, List.of(line), org, 2).getFirst().eventId()).isNotEqualTo(events.getFirst().eventId());
    }

    @Test
    void onlyAcceptsExactApprovedOriginsForTheAssignedOrganization() {
        var client = new LokiClient(new ObjectMapper(), "", org.toString(), "", "",
                "http://loki:3100,https://approved.example.test");
        assertThat(client.effectiveEndpoint(org, null)).isNull();
        assertThat(client.approvedEndpoint(org, "http://loki:3100/")).isEqualTo(endpoint);
        assertThat(client.approvedEndpoint(org, "https://approved.example.test")).isNotNull();
        assertThat(client.approvedEndpoint(org, "https://approved.example.test@other.example.test")).isNull();
        assertThat(client.approvedEndpoint(org, "https://approved.example.test/logs")).isNull();
        assertThat(client.approvedEndpoint(org, "http://loki:3101")).isNull();
        assertThat(client.approvedEndpoint(UUID.randomUUID(), "http://loki:3100")).isNull();
    }

    @Test
    void requiresHttpsForOperatorCredentials() {
        assertThatThrownBy(() -> new LokiClient(new ObjectMapper(), "http://loki:3100", org.toString(),
                "Bearer placeholder", "", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
