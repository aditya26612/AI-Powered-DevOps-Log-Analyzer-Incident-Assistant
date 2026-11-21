package com.project.log_layer.tenant;

import com.project.log_layer.appintegration.repository.ApplicationIntegrationRepository;
import com.project.log_layer.entity.Log;
import com.project.log_layer.enums.LogLevel;
import com.project.log_layer.notification.config.NotificationProperties;
import com.project.log_layer.notification.service.impl.SlackNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TenantSlackRoutingTest {
    @Test
    void dispatchesOnlyEachVerifiedApplicationDestination() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String first = "https://hooks.slack.com/services/Tfixture/Bfixture/test-a";
        String second = "https://hooks.slack.com/services/Tfixture/Bfixture/test-b";
        var routes = new ArrayList<URI>();
        var client = WebClient.builder().exchangeFunction(request -> {
            routes.add(request.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });
        var repository = mock(ApplicationIntegrationRepository.class);
        var cipher = new WebhookCipher("cf".repeat(32));
        var appA = app(1L, a, first, cipher);
        var appB = app(2L, b, second, cipher);
        when(repository.findSlackRoute(1L, a))
                .thenReturn(Optional.of(appA));
        when(repository.findSlackRoute(2L, b))
                .thenReturn(Optional.of(appB));
        var properties = new NotificationProperties();
        properties.getSlack().setEnabled(true);
        var service = new SlackNotificationService(properties, client, repository, cipher);

        service.sendIncidentAlert(log(1L, a), null);
        service.sendIncidentAlert(log(2L, b), null);
        service.sendIncidentAlert(log(1L, b), null);
        assertEquals(List.of(URI.create(first), URI.create(second)), routes);
    }

    private ApplicationIntegrationRepository.SlackRoute app(long id, UUID organization, String url, WebhookCipher cipher) {
        var route = mock(ApplicationIntegrationRepository.SlackRoute.class);
        when(route.getCiphertext()).thenReturn(cipher.encrypt(organization, id, url));
        when(route.getAlertOnError()).thenReturn(true);
        return route;
    }

    private Log log(long application, UUID organization) {
        return Log.builder().id(application).organizationId(organization).applicationId(application)
                .level(LogLevel.ERROR).applicationName("payments").message("Fixture alert").build();
    }
}
