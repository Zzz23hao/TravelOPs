package com.travelops.service;

import com.travelops.dto.RouteSearchResponse;
import com.travelops.dto.RouteSearchType;
import com.travelops.entity.RouteSearchLog;
import com.travelops.entity.RouteSearchLog.Outcome;
import com.travelops.exception.ApiException;
import com.travelops.exception.ErrorResponse;
import com.travelops.repository.RouteSearchLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.Map;

/**
 * Calls the analytics API's route search and records every search.
 *
 * Deliberately not @Transactional: the HTTP call must not hold a database connection while it
 * waits, and the log row must commit even when the search ends in an error that is rethrown.
 */
@Service
public class RouteIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(RouteIntegrationService.class);

    // The only analytics errors that are answers rather than failures. Any other 4xx (for example
    // a generic NOT_FOUND from a wrong path) means the two services disagree on the contract.
    private static final Map<String, HttpStatus> PASS_THROUGH_ERRORS = Map.of(
            "NO_ROUTE_FOUND", HttpStatus.NOT_FOUND,
            "AIRPORT_NOT_FOUND", HttpStatus.NOT_FOUND,
            "INVALID_PARAMETER", HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED", HttpStatus.BAD_REQUEST);

    private final RestClient analyticsClient;
    private final RouteSearchLogRepository logRepository;

    public RouteIntegrationService(RestClient analyticsClient, RouteSearchLogRepository logRepository) {
        this.analyticsClient = analyticsClient;
        this.logRepository = logRepository;
    }

    public RouteSearchResponse search(Long userId, RouteSearchType type, String from, String to) {
        String source = from.toUpperCase(Locale.ROOT);
        String target = to.toUpperCase(Locale.ROOT);
        if (source.equals(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "from and to must be different airports.");
        }

        long started = System.nanoTime();
        try {
            RouteSearchResponse route = callAnalytics(type, source, target);
            record(userId, type, source, target, Outcome.FOUND, route.summary(), started);
            return route;
        } catch (ApiException e) {
            record(userId, type, source, target, outcomeOf(e), e.getCode() + ": " + e.getMessage(), started);
            throw e;
        }
    }

    private RouteSearchResponse callAnalytics(RouteSearchType type, String source, String target) {
        RouteSearchResponse route;
        try {
            route = analyticsClient.get()
                    .uri(uri -> uri.path("/routes/{type}").queryParam("from", source).queryParam("to", target)
                            .build(type.path()))
                    .retrieve()
                    .body(RouteSearchResponse.class);
        } catch (HttpClientErrorException e) {
            ErrorResponse error = readError(e);
            HttpStatus status = error == null ? null : PASS_THROUGH_ERRORS.get(error.error());
            if (status != null && status.value() == e.getStatusCode().value()) {
                throw new ApiException(status, error.error(), error.message());
            }
            log.error("Analytics API returned unexpected {} for {} {}->{}: {}",
                    e.getStatusCode(), type.path(), source, target, e.getResponseBodyAsString());
            throw unavailable();
        } catch (RestClientException e) {
            // Timeouts, refused connections, unknown host, any 5xx and unreadable bodies all land here.
            log.warn("Analytics API call failed for {} {}->{}: {}", type.path(), source, target, e.toString());
            throw unavailable();
        }

        if (route == null || !route.isComplete()) {
            log.error("Analytics API returned an incomplete route for {} {}->{}: {}", type.path(), source, target, route);
            throw unavailable();
        }
        return route;
    }

    private static ErrorResponse readError(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(ErrorResponse.class);
        } catch (RuntimeException notOurErrorShape) {
            return null;
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROUTE_SERVICE_UNAVAILABLE",
                "Route search service is temporarily unavailable. Please try again later.");
    }

    private static Outcome outcomeOf(ApiException e) {
        return switch (e.getStatus()) {
            case NOT_FOUND -> Outcome.NOT_FOUND;
            case BAD_REQUEST -> Outcome.REJECTED;
            default -> Outcome.UNAVAILABLE;
        };
    }

    // Best effort: a failed audit write is logged, but must not turn a found route into an error.
    private void record(Long userId, RouteSearchType type, String source, String target,
                        Outcome outcome, String summary, long startedNanos) {
        int latencyMs = (int) ((System.nanoTime() - startedNanos) / 1_000_000);
        try {
            logRepository.save(new RouteSearchLog(userId, source, target, type.path(), outcome, summary, latencyMs));
        } catch (RuntimeException e) {
            log.warn("Could not save route search log for user {}: {}", userId, e.toString());
        }
    }
}
