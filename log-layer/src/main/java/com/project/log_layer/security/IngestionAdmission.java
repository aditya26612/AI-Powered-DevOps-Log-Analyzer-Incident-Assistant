package com.project.log_layer.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class IngestionAdmission {
    public static final int MAX_BODY_BYTES = 65536;
    private final int perIdentity;
    private final Map<String, Integer> requests = new HashMap<>();
    private long minute;

    public IngestionAdmission(@Value("${tenant.limits.ingest-per-minute:120}") int perIdentity) {
        if (perIdentity <= 0) throw new IllegalArgumentException("Invalid ingestion limit");
        this.perIdentity = perIdentity;
    }

    public boolean allowApplication(long application) {
        return allowApplication(application, 1);
    }

    public boolean allowApplication(long application, int events) {
        return allow("app:" + application, perIdentity, events);
    }

    public boolean allowManual(String organization) {
        return allow("manual:" + organization, perIdentity, 1);
    }

    public boolean allowAuthentication(String remote) {
        return allow("remote:" + remote, 600, 1);
    }

    private synchronized boolean allow(String identity, int limit, int weight) {
        long now = System.currentTimeMillis() / 60000;
        if (minute != now) {
            requests.clear();
            minute = now;
        }
        int current = requests.getOrDefault(identity, 0);
        if (weight > limit - current || (current == 0 && requests.size() >= 10000)) {
            return false;
        }
        requests.put(identity, current + weight);
        return true;
    }

    public static HttpServletRequest boundedBody(HttpServletRequest request) throws IOException {
        byte[] data = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (data.length > MAX_BODY_BYTES) {
            return null;
        }
        var bytes = new ByteArrayInputStream(data);
        var stream = new ServletInputStream() {
            @Override public int read() { return bytes.read(); }
            @Override public int read(byte[] buffer, int off, int len) { return bytes.read(buffer, off, len); }
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
        };
        return new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() { return stream; }
            @Override public int getContentLength() { return data.length; }
            @Override public long getContentLengthLong() { return data.length; }
        };
    }
}
