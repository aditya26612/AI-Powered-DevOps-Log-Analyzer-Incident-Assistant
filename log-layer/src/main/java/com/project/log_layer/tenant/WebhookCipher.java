package com.project.log_layer.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class WebhookCipher {
    private static final String VERSION = "gcm1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public WebhookCipher(@Value("${SLACK_WEBHOOK_ENCRYPTION_KEY:}") String configured) {
        if (configured.isBlank()) {
            key = null;
        } else if (configured.matches("[0-9a-fA-F]{64}")) {
            key = new SecretKeySpec(HexFormat.of().parseHex(configured), "AES");
        } else {
            throw new IllegalStateException("Webhook encryption requires a 32-byte hexadecimal key");
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(UUID org, long app, String url) {
        if (!allowed(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook must be an HTTPS Slack incoming webhook");
        }
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, org, app, nonce, url.getBytes(StandardCharsets.UTF_8));
        var enc = Base64.getUrlEncoder().withoutPadding();
        return VERSION + enc.encodeToString(nonce) + ":" + enc.encodeToString(encrypted);
    }

    public String decrypt(UUID org, long app, String stored) {
        if (stored == null || !stored.startsWith(VERSION)) {
            throw new IllegalStateException("Unrecognized webhook ciphertext");
        }
        String[] fields = stored.substring(VERSION.length()).split(":", -1);
        if (fields.length != 2) {
            throw new IllegalStateException("Invalid webhook ciphertext");
        }
        try {
            var dec = Base64.getUrlDecoder();
            byte[] nonce = dec.decode(fields[0]);
            if (nonce.length != 12) {
                throw new IllegalStateException("Invalid webhook nonce");
            }
            String url = new String(crypt(Cipher.DECRYPT_MODE, org, app, nonce, dec.decode(fields[1])),
                    StandardCharsets.UTF_8);
            if (!allowed(url)) {
                throw new IllegalStateException("Invalid encrypted webhook destination");
            }
            return url;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid webhook ciphertext");
        }
    }

    static boolean allowed(String url) {
        if (url == null || url.length() > 500) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && "hooks.slack.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getRawPath().matches("/services/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] crypt(int mode, UUID org, long app, byte[] nonce, byte[] value) {
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook encryption is unavailable");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("devinsight/slack/gcm1/" + org + "/" + app).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Webhook encryption authentication failed");
        }
    }
}
