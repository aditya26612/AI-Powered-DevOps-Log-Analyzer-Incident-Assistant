package com.project.log_layer.notification.outbox;

import com.project.log_layer.tenant.WebhookCipher;
import io.netty.channel.ChannelOption;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Component
class SlackDeliveryClient {
    private static final long MAX_RETRY_AFTER_SECONDS = Duration.ofDays(365).toSeconds();
    private final DeliverySettings settings;
    private final URI fixture;
    private final WebClient client;

    SlackDeliveryClient(DeliverySettings settings, Environment environment) {
        this.settings = settings;
        if (settings.transport() == DeliverySettings.Transport.SLACK
                && !environment.getProperty("notification.delivery.external",Boolean.class,false)) {
            throw new IllegalStateException("External Slack dispatch requires an explicit enable switch");
        }
        if (settings.transport() == DeliverySettings.Transport.FIXTURE) {
            if (!environment.acceptsProfiles(Profiles.of("stage08-fixture"))) {
                throw new IllegalStateException("Notification fixture requires the stage08-fixture profile");
            }
            fixture = fixtureUri(settings.fixtureUrl());
        } else {
            fixture = null;
        }
        var http = HttpClient.create().noProxy().followRedirect(false)
                .disableRetry(true).option(ChannelOption.CONNECT_TIMEOUT_MILLIS,2000)
                .responseTimeout(settings.httpTimeout());
        if (settings.transport() == DeliverySettings.Transport.SLACK) {
            http = http.resolver(new SlackAddressResolver());
        }
        client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024))
                .build();
    }

    NotificationQueue.Result send(NotificationQueue.Work work) {
        if (settings.transport() == DeliverySettings.Transport.OFF) {
            return result("CANCELLED","TRANSPORT_DISABLED",null,0);
        }
        if (fixture == null && !WebhookCipher.allowed(work.destination())) {
            return result("FAILED_TERMINAL","DESTINATION_BLOCKED",null,0);
        }
        URI endpoint = fixture == null ? URI.create(work.destination()) : fixture;
        try {
            var response = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(SlackMessage.payload(work.evidence()))
                    .exchangeToMono(reply -> {
                        int status = reply.statusCode().value();
                        String wait = reply.headers().asHttpHeaders().getFirst("Retry-After");
                        if (status != 200) {
                            return Mono.just(classify(status,null,null,wait));
                        }
                        var media = reply.headers().contentType().orElse(null);
                        return reply.bodyToMono(String.class).defaultIfEmpty("")
                                .map(body -> classify(status,media,body,wait));
                    }).block(settings.httpTimeout());
            return response == null ? result("DELIVERY_UNKNOWN","RESPONSE_MISSING",null,0) : response;
        } catch (RuntimeException ex) {
            for (Throwable current = ex; current != null; current = current.getCause()) {
                if (current instanceof DestinationBlocked) {
                    return result("FAILED_TERMINAL","DESTINATION_BLOCKED",null,0);
                }
                if (current instanceof ConnectException || current instanceof UnknownHostException) {
                    return result("RETRY_WAIT","CONNECTION_UNAVAILABLE",null,0);
                }
            }
            return result("DELIVERY_UNKNOWN","TRANSPORT_UNCERTAIN",null,0);
        }
    }

    private static final class DestinationBlocked extends RuntimeException {}

    /** Validates the addresses used by the channel rather than making a separate DNS preflight request. */
    private static final class SlackAddressResolver extends AddressResolverGroup<InetSocketAddress> {
        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            return new AbstractAddressResolver<>(executor,InetSocketAddress.class) {
                @Override
                protected boolean doIsResolved(InetSocketAddress address) { return false; }

                @Override
                protected void doResolve(InetSocketAddress address, Promise<InetSocketAddress> promise) throws UnknownHostException {
                    promise.setSuccess(approved(address).getFirst());
                }

                @Override
                protected void doResolveAll(InetSocketAddress address, Promise<List<InetSocketAddress>> promise) throws UnknownHostException {
                    promise.setSuccess(approved(address));
                }
            };
        }

        private static List<InetSocketAddress> approved(InetSocketAddress address) throws UnknownHostException {
            if (!"hooks.slack.com".equalsIgnoreCase(address.getHostString()) || address.getPort() != 443) {
                throw new DestinationBlocked();
            }
            var resolved = Arrays.asList(InetAddress.getAllByName(address.getHostString()));
            if (resolved.isEmpty() || resolved.stream().anyMatch(ip -> !globallyRoutable(ip))) {
                throw new DestinationBlocked();
            }
            return resolved.stream().map(ip -> new InetSocketAddress(ip,443)).toList();
        }
    }

    static NotificationQueue.Result classify(int status, MediaType type, String body, String retryAfter) {
        if (status < 100 || status > 599) {
            return result("DELIVERY_UNKNOWN","INVALID_PROVIDER_STATUS",null,0);
        }
        if (status == 200) {
            return type != null && MediaType.TEXT_PLAIN.includes(type) && body != null && body.strip().equals("ok")
                    ? result("DELIVERED",null,200,0)
                    : result("DELIVERY_UNKNOWN","UNEXPECTED_SUCCESS_BODY",200,0);
        }
        if (status == 429) {
            return result("RETRY_WAIT","RATE_LIMITED",status,retryAfterSeconds(retryAfter));
        }
        if (status == 503) {
            return result("RETRY_WAIT","PROVIDER_UNAVAILABLE",status,0);
        }
        if (status >= 500 || status == 408 || status >= 200 && status < 300) {
            return result("DELIVERY_UNKNOWN","PROVIDER_UNCERTAIN",status,0);
        }
        return result("FAILED_TERMINAL",status >= 300 && status < 400 ? "REDIRECT_BLOCKED"
                : "PROVIDER_REJECTED",status,0);
    }

    private static long retryAfterSeconds(String value) {
        if (value == null) {
            return 0;
        }
        String header = value.strip();
        if (header.matches("[0-9]+")) {
            try {
                return Math.min(Long.parseLong(header), MAX_RETRY_AFTER_SECONDS);
            } catch (NumberFormatException ex) {
                return MAX_RETRY_AFTER_SECONDS;
            }
        }
        try {
            Instant after = ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration remaining = Duration.between(Instant.now(),after);
            long rounded = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
            return Math.min(MAX_RETRY_AFTER_SECONDS, Math.max(0, rounded));
        } catch (DateTimeParseException ex) {
            return 0;
        }
    }

    static boolean globallyRoutable(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 16) {
            // Global unicast only. Disallow IPv4 transition/perimeter-bypass address families.
            return (raw[0] & 0xe0) == 0x20 && !(raw[0] == 0x20 && (raw[1] == 0x02 || raw[1] == 0x01 && raw[2] == 0));
        }
        int a = raw[0] & 255;
        int b = raw[1] & 255;
        return a > 0 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 198 && (b == 18 || b == 19)) && !(a == 192 && b == 0);
    }

    private static URI fixtureUri(String value) {
        if (value == null || !value.matches("http://127\\.0\\.0\\.1:[0-9]{4,5}/notification")) {
            throw new IllegalStateException("Invalid notification fixture receiver");
        }
        URI uri = URI.create(value);
        if (uri.getPort() < 1024 || uri.getPort() > 65535 || uri.getPort() == 55438) {
            throw new IllegalStateException("Invalid notification fixture receiver port");
        }
        return uri;
    }

    private static NotificationQueue.Result result(String state, String reason, Integer status, long delay) {
        return new NotificationQueue.Result(state,reason,status,delay);
    }
}
