package com.project.log_layer.dto.request.ingest;

import java.util.regex.Pattern;

public record TraceContext(String parent, Origin origin) {
    public enum Origin { COLLECTOR_REPORTED, INBOUND_REPORTED, LOCAL_ROOT_REPORTED, EXPLICIT }

    private static final Pattern V00 = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");

    public static TraceContext parse(String parent, Origin origin) {
        if (parent == null && origin == null) {
            return null;
        }
        if (parent == null || parent.length() != 55) {
            throw new IllegalArgumentException("Invalid trace context");
        }
        var match = V00.matcher(parent);
        if (!match.matches() || match.group(1).equals("0".repeat(32)) || match.group(2).equals("0".repeat(16))) {
            throw new IllegalArgumentException("Invalid trace context");
        }
        return new TraceContext(parent, origin == null ? Origin.COLLECTOR_REPORTED : origin);
    }

    public String traceId() { return parent.substring(3, 35); }
}
