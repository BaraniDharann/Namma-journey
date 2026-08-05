package com.travelplatform.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Builds {@link RestTemplate}s for calls that leave this process.
 *
 * <p>A bare {@code new RestTemplate()} has <strong>no connect or read timeout at all</strong>.
 * Every outbound call here is to a third party (routing, geocoding), so an unresponsive
 * provider does not fail — it hangs, holding a Tomcat request thread for as long as the socket
 * stays open. With a 200-thread pool and one slow dependency, that is how a degraded upstream
 * turns into a full outage of an otherwise healthy application.
 *
 * <p>Both callers already fall back to a local calculation when the request throws, so a
 * bounded failure is strictly better than an unbounded wait.
 */
public final class ExternalHttpClients {

    /** Long enough for a healthy provider on a slow link, short enough to fail before the caller does. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private ExternalHttpClients() {
    }

    public static RestTemplate forThirdPartyApis() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return new RestTemplate(factory);
    }
}
