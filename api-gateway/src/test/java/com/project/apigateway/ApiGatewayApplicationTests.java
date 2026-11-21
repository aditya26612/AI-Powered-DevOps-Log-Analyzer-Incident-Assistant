package com.project.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    @Autowired
    private WebTestClient client;

    @LocalServerPort
    private int port;

    @Test
    void healthIsMinimalAndOtherEndpointsStayProtected() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist()
                .jsonPath("$.details").doesNotExist();
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/logs/1").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void permitsTenantHeaderOnlyFromConfiguredLocalOrigins() {
        WebTestClient browser = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port).build();
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            browser.options().uri("/api/v1/logs/search")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,x-devinsight-organization")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                    .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                            "(?i).*X-DevInsight-Organization.*");
        }

        browser.options().uri("/api/v1/logs/search")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void contextLoads() {
    }

}
