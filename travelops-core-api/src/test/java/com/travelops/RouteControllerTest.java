package com.travelops;

import com.travelops.controller.RouteController;
import com.travelops.dto.RouteSearchResponse;
import com.travelops.dto.RouteSearchType;
import com.travelops.exception.ApiException;
import com.travelops.security.JwtService;
import com.travelops.security.SecurityConfig;
import com.travelops.service.RouteIntegrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "jwt.expiration-minutes=60"})
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private RouteIntegrationService routeIntegrationService;

    private String bearer() {
        return "Bearer " + jwtService.generateToken(7L);
    }

    @Test
    void routeSearchRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/routes/shortest").param("from", "YYZ").param("to", "LAX"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(routeIntegrationService);
    }

    @Test
    void foundRouteIsReturnedInSnakeCase() throws Exception {
        when(routeIntegrationService.search(7L, RouteSearchType.CHEAPEST, "YYZ", "LAX")).thenReturn(
                new RouteSearchResponse("YYZ", "LAX", "cheapest", List.of("YYZ", "JFK", "DEN", "LAX"),
                        List.of("F006", "F007", "F008"), 4595.0, 520, 280.0, 2));

        mockMvc.perform(get("/api/routes/cheapest").param("from", "YYZ").param("to", "LAX")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route[1]").value("JFK"))
                .andExpect(jsonPath("$.total_price").value(280.0))
                .andExpect(jsonPath("$.flight_ids[0]").value("F006"))
                .andExpect(jsonPath("$.complete").doesNotExist());
    }

    @Test
    void routeServiceOutageIs503WithTypedErrorBody() throws Exception {
        when(routeIntegrationService.search(anyLong(), any(), anyString(), anyString())).thenThrow(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROUTE_SERVICE_UNAVAILABLE",
                        "Route search service is temporarily unavailable. Please try again later."));

        mockMvc.perform(get("/api/routes/min-transfers").param("from", "YYZ").param("to", "LAX")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ROUTE_SERVICE_UNAVAILABLE"));
    }

    @Test
    void malformedAirportCodeIs400BeforeAnyDownstreamCall() throws Exception {
        mockMvc.perform(get("/api/routes/shortest").param("from", "TORONTO").param("to", "LAX")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("from: must be a three-letter airport code"));
        verifyNoInteractions(routeIntegrationService);
    }

    @Test
    void missingParameterIs400() throws Exception {
        mockMvc.perform(get("/api/routes/shortest").param("from", "YYZ").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }
}
