package com.project.log_layer.notification.outbox;

import com.project.log_layer.tenant.WebhookCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetAddress;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class SlackDeliverySafetyTest {
    @ParameterizedTest
    @ValueSource(strings={"http://127.0.0.1:8084/notification",
            "https://hooks.slack.com.invalid/services/FIXTURE/NOT_REAL/value",
            "https://hooks.slack.com@127.0.0.1/services/FIXTURE/NOT_REAL/value",
            "https://hooks.slack.com/services/FIXTURE/%2e%2e/value",
            "https://hooks.slack.com/services/FIXTURE/NOT_REAL/value?x=y"})
    void rejectsNonCanonicalWebhookWithoutOpeningAConnection(String candidate) {
        assertFalse(WebhookCipher.allowed(candidate));
    }

    @ParameterizedTest
    @ValueSource(strings={"127.0.0.1","169.254.169.254","10.0.0.1","100.64.0.1","::1","fc00::1","fe80::1"})
    void rejectsUnsafeResolvedAddresses(String literal) throws Exception {
        assertFalse(SlackDeliveryClient.globallyRoutable(InetAddress.getByName(literal)));
    }

    @ParameterizedTest
    @ValueSource(ints={400,403,404,410})
    void permanentProviderRejectionsTerminate(int status) {
        var result=SlackDeliveryClient.classify(status,null,null,null);
        assertEquals("FAILED_TERMINAL",result.status());
        assertEquals(status,result.httpStatus());
    }

    @Test
    void providerSuccessRequiresTextOkAndRetryAfterIsNeverShortenedWithinLifetime() {
        assertEquals("DELIVERED",SlackDeliveryClient.classify(200,MediaType.TEXT_PLAIN,"ok\n",null).status());
        assertEquals("DELIVERY_UNKNOWN",SlackDeliveryClient.classify(200,MediaType.APPLICATION_JSON,"ok",null).status());
        assertEquals("DELIVERY_UNKNOWN",SlackDeliveryClient.classify(200,MediaType.TEXT_PLAIN,"unexpected",null).status());
        assertEquals("DELIVERY_UNKNOWN",SlackDeliveryClient.classify(500,null,null,null).status());
        assertEquals(180,SlackDeliveryClient.classify(429,null,null,"180").retryAfterSeconds());
        String date = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(3).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(SlackDeliveryClient.classify(429,null,null,date).retryAfterSeconds() >= 179);
        assertEquals(0,SlackDeliveryClient.classify(429,null,null,"unknown").retryAfterSeconds());
        assertTrue(SlackDeliveryClient.classify(429,null,null,"9999999999999999999999999").retryAfterSeconds()
                > Duration.ofDays(7).toSeconds());
    }

    @Test
    void testModeCannotBeEnabledByProvidingATenantDestination() {
        var local=new MockEnvironment();
        assertThrows(IllegalStateException.class,()->new SlackDeliveryClient(
                settings(DeliverySettings.Transport.FIXTURE,"http://127.0.0.1:12345/notification"),local));
        local.setActiveProfiles("stage08-fixture");
        assertThrows(IllegalStateException.class,()->new SlackDeliveryClient(
                settings(DeliverySettings.Transport.FIXTURE,"http://localhost:12345/notification"),local));
        assertThrows(IllegalStateException.class,()->new SlackDeliveryClient(
                settings(DeliverySettings.Transport.SLACK,null),local));
    }

    private DeliverySettings settings(DeliverySettings.Transport transport,String fixture) {
        return new DeliverySettings(transport,true,1,Duration.ofMillis(500),Duration.ofSeconds(12),
                Duration.ofSeconds(1),Duration.ofSeconds(8),Duration.ofHours(1),2,fixture);
    }
}
