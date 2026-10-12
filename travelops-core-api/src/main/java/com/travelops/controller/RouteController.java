package com.travelops.controller;

import com.travelops.dto.RouteSearchResponse;
import com.travelops.dto.RouteSearchType;
import com.travelops.security.AuthUser;
import com.travelops.service.RouteIntegrationService;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxies route searches to the analytics API. Requires login, because every search is logged
 * against the user who made it.
 */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private static final String AIRPORT_CODE = "^[A-Za-z]{3}$";
    private static final String AIRPORT_CODE_MESSAGE = "must be a three-letter airport code";

    private final RouteIntegrationService routeIntegrationService;

    public RouteController(RouteIntegrationService routeIntegrationService) {
        this.routeIntegrationService = routeIntegrationService;
    }

    @GetMapping("/shortest")
    public RouteSearchResponse shortest(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam @Pattern(regexp = AIRPORT_CODE, message = AIRPORT_CODE_MESSAGE) String from,
            @RequestParam @Pattern(regexp = AIRPORT_CODE, message = AIRPORT_CODE_MESSAGE) String to) {
        return routeIntegrationService.search(user.id(), RouteSearchType.SHORTEST, from, to);
    }

    @GetMapping("/cheapest")
    public RouteSearchResponse cheapest(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam @Pattern(regexp = AIRPORT_CODE, message = AIRPORT_CODE_MESSAGE) String from,
            @RequestParam @Pattern(regexp = AIRPORT_CODE, message = AIRPORT_CODE_MESSAGE) String to) {
        return routeIntegrationService.search(user.id(), RouteSearchType.CHEAPEST, from, to);
    }

    @GetMapping("/min-transfers")
    public RouteSearchResponse minTransfers(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam @Pattern(regexp = AIRPORT_CODE, message = AIRPORT_CODE_MESSAGE) String from,
            @RequestParam @Pattern(regexp = AIRPORT_CODE, message = AIRPORT_CODE_MESSAGE) String to) {
        return routeIntegrationService.search(user.id(), RouteSearchType.MIN_TRANSFERS, from, to);
    }
}
