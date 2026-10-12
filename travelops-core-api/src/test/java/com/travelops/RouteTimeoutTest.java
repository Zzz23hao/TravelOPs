package com.travelops;

import com.sun.net.httpserver.HttpServer;
import com.travelops.config.AnalyticsClientConfig;
import com.travelops.dto.RouteSearchType;
import com.travelops.exception.ApiException;
import com.travelops.repository.RouteSearchLogRepository;
import com.travelops.service.RouteIntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * MockRestServiceServer can only pretend a timeout happened. This test uses a real local HTTP
 * server that answers too slowly, so it proves the configured 2 s read timeout actually fires.
 */
class RouteTimeoutTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final long SERVER_DELAY_MS = 5_000;

    private HttpServer slowAnalytics;
    private ExecutorService handlerThreads;

    @BeforeEach
    void startSlowServer() throws IOException {
        slowAnalytics = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        slowAnalytics.createContext("/routes/shortest", exchange -> {
            try {
                Thread.sleep(SERVER_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        handlerThreads = Executors.newCachedThreadPool();
        slowAnalytics.setExecutor(handlerThreads);
        slowAnalytics.start();
    }

    @AfterEach
    void stopSlowServer() {
        // Interrupt the still-sleeping handler so the test does not wait out the full delay.
        handlerThreads.shutdownNow();
        slowAnalytics.stop(0);
    }

    @Test
    void slowAnalyticsResponseIsCutOffAtTheReadTimeoutAndBecomes503() {
        String baseUrl = "http://localhost:" + slowAnalytics.getAddress().getPort();
        RestClient client = AnalyticsClientConfig.build(RestClient.builder(), baseUrl, TIMEOUT, TIMEOUT);
        RouteIntegrationService service = new RouteIntegrationService(client, mock(RouteSearchLogRepository.class));

        long started = System.nanoTime();
        assertThatThrownBy(() -> service.search(7L, RouteSearchType.SHORTEST, "YYZ", "LAX"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getCode()).isEqualTo("ROUTE_SERVICE_UNAVAILABLE");
                });
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        // Gave up at about 2 s, well before the server's 5 s answer.
        assertThat(elapsedMs).isBetween(TIMEOUT.toMillis(), SERVER_DELAY_MS - 1_000);
    }
}
