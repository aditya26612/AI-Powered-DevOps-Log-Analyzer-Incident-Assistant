package com.project.log_layer.tenant;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookCipherTest {
    private static final String DESTINATION = "https://hooks.slack.com/services/Tfixture/Bfixture/example";
    private final UUID tenant = UUID.randomUUID();

    @Test
    void randomCiphertextAuthenticatesOrganizationAndApplication() {
        var cipher = new WebhookCipher("a1".repeat(32));
        String ciphertext = cipher.encrypt(tenant, 5L, DESTINATION);
        assertNotEquals(ciphertext, cipher.encrypt(tenant, 5L, DESTINATION));
        assertFalse(ciphertext.contains(DESTINATION));
        assertEquals(DESTINATION, cipher.decrypt(tenant, 5L, ciphertext));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(UUID.randomUUID(), 5L, ciphertext));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tenant, 6L, ciphertext));
        var other = new WebhookCipher("01".repeat(32));
        assertThrows(IllegalStateException.class, () -> other.decrypt(tenant, 5L, ciphertext));
        String changed = ciphertext.replaceFirst("gcm1:[A-Za-z0-9_-]+:", "gcm1:AAAAAAAAAAAAAAAA:");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tenant, 5L, changed));
    }

    @Test
    void allowsOnlyNativeSlackWebhookShape() {
        assertTrue(WebhookCipher.allowed(DESTINATION));
        assertFalse(WebhookCipher.allowed("http://hooks.slack.com/services/T/B/secret"));
        assertFalse(WebhookCipher.allowed("https://other.example.test/services/T/B/secret"));
        assertFalse(WebhookCipher.allowed(DESTINATION + "?unexpected=value"));
    }
}
