package com.oncontrol.oncontrolbackend.imaging.infrastructure.client;

import com.oncontrol.oncontrolbackend.imaging.application.dto.LungSegmentationResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Segmentation on AKS takes ~30 s (162 MB study download + ITK). The client must honour the configured read
 * timeout instead of the HTTP library default (10 s for Reactor Netty), which turned slow segmentations into 500s.
 */
class ImagingServiceClientTimeoutTest {

    private static final long DELAY_MS = 1_500;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startSlowImaging() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/studies/", exchange -> {
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readTimeoutShorterThanImaging_failsFast() {
        ImagingServiceClient client = new ImagingServiceClient(baseUrl, Duration.ofSeconds(1), Duration.ofMillis(500));

        assertThatThrownBy(() -> client.segmentLungs("study-1", -320, 2, false))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void readTimeoutLongerThanImaging_waitsForTheResult() {
        ImagingServiceClient client = new ImagingServiceClient(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(5));

        LungSegmentationResponse response = client.segmentLungs("study-1", -320, 2, false);

        assertThat(response).isNotNull();
    }
}
