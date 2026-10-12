package com.travelops;

import com.travelops.dto.RouteSearchResponse;
import com.travelops.dto.RouteSearchType;
import com.travelops.entity.RouteSearchLog;
import com.travelops.entity.RouteSearchLog.Outcome;
import com.travelops.exception.ApiException;
import com.travelops.repository.RouteSearchLogRepository;
import com.travelops.service.RouteIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The analytics API is replaced by MockRestServiceServer: every downstream outcome is scripted,
 * including I/O failures, and no network or database is used.
 */
@ExtendWith(MockitoExtension.class)
class RouteIntegrationServiceTest {

    private static final String BASE_URL = "http://analytics";
    private static final String SHORTEST_URL = BASE_URL + "/routes/shortest?from=YYZ&to=LAX";
    private static final String ROUTE_JSON = """
            {"from": "YYZ", "to": "LAX", "search_type": "shortest", "route": ["YYZ", "ORD", "LAX"],
             "flight_ids": ["F002", "F003"], "total_distance_km": 3507.0, "total_duration_minutes": 355,
             "total_price": 420.0, "transfers": 1}
            """;

    @Mock
    private RouteSearchLogRepository logRepository;

    private MockRestServiceServer analytics;
    private RouteIntegrationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        analytics = MockRestServiceServer.bindTo(builder).build();
        service = new RouteIntegrationService(builder.build(), logRepository);
    }

    @Test
    void foundRouteIsReturnedAndLogged() {
        analytics.expect(requestTo(SHORTEST_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ROUTE_JSON, MediaType.APPLICATION_JSON));

        RouteSearchResponse route = service.search(7L, RouteSearchType.SHORTEST, "yyz", "lax");

        assertThat(route.route()).containsExactly("YYZ", "ORD", "LAX");
        assertThat(route.totalDistanceKm()).isEqualTo(3507.0);
        RouteSearchLog saved = savedLog();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getOutcome()).isEqualTo(Outcome.FOUND);
        assertThat(saved.getSearchType()).isEqualTo("shortest");
        assertThat(saved.getResultSummary()).startsWith("YYZ>ORD>LAX");
        analytics.verify();
    }

    @Test
    void minTransfersUsesTheHyphenatedPath() {
        analytics.expect(requestTo(BASE_URL + "/routes/min-transfers?from=YYZ&to=LAX"))
                .andRespond(withSuccess(ROUTE_JSON, MediaType.APPLICATION_JSON));

        service.search(7L, RouteSearchType.MIN_TRANSFERS, "YYZ", "LAX");

        analytics.verify();
    }

    @Test
    void timeoutBecomes503RouteServiceUnavailable() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertUnavailable();
        assertThat(savedLog().getOutcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void connectionRefusedBecomes503() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertUnavailable();
    }

    @Test
    void analyticsServerErrorBecomes503() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertUnavailable();
    }

    @Test
    void analyticsDatabaseOutageBecomes503() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"DATABASE_UNAVAILABLE\", \"message\": \"Flight database is down.\"}"));

        assertUnavailable();
    }

    @Test
    void noRouteIsAnAnswerNotAFailureAndPassesThroughAs404() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"NO_ROUTE_FOUND\", \"message\": \"No route from YYZ to LAX.\"}"));

        assertThatThrownBy(() -> service.search(7L, RouteSearchType.SHORTEST, "YYZ", "LAX"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.getCode()).isEqualTo("NO_ROUTE_FOUND");
                    assertThat(api.getMessage()).isEqualTo("No route from YYZ to LAX.");
                });
        assertThat(savedLog().getOutcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void genericNotFoundMeansAContractMismatchSoItIs503() {
        // A wrong path on the analytics side is a deployment problem, not "no route".
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"NOT_FOUND\", \"message\": \"Not Found\"}"));

        assertUnavailable();
    }

    @Test
    void incompleteResponseBodyBecomes503() {
        analytics.expect(requestTo(SHORTEST_URL))
                .andRespond(withSuccess("{\"from\": \"YYZ\", \"to\": \"LAX\"}", MediaType.APPLICATION_JSON));

        assertUnavailable();
    }

    @Test
    void unreadableResponseBodyBecomes503() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        assertUnavailable();
    }

    @Test
    void failingToSaveTheLogDoesNotFailTheSearch() {
        analytics.expect(requestTo(SHORTEST_URL)).andRespond(withSuccess(ROUTE_JSON, MediaType.APPLICATION_JSON));
        when(logRepository.save(any())).thenThrow(new IllegalStateException("database down"));

        RouteSearchResponse route = service.search(7L, RouteSearchType.SHORTEST, "YYZ", "LAX");

        assertThat(route.transfers()).isEqualTo(1);
    }

    @Test
    void sameOriginAndDestinationIsRejectedWithoutCallingAnalytics() {
        assertThatThrownBy(() -> service.search(7L, RouteSearchType.SHORTEST, "YYZ", "yyz"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        analytics.verify();
        verifyNoInteractions(logRepository);
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> service.search(7L, RouteSearchType.SHORTEST, "YYZ", "LAX"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.getCode()).isEqualTo("ROUTE_SERVICE_UNAVAILABLE");
                });
        analytics.verify();
    }

    private RouteSearchLog savedLog() {
        ArgumentCaptor<RouteSearchLog> captor = ArgumentCaptor.forClass(RouteSearchLog.class);
        verify(logRepository).save(captor.capture());
        return captor.getValue();
    }
}
