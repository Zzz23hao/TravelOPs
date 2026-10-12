package com.travelops.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * The analytics API's route response, passed through to our clients unchanged.
 * Snake case is declared here because it is part of the contract with the Python service,
 * not just this app's own JSON setting. Unknown fields are ignored, so Python can add fields
 * without breaking this side.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RouteSearchResponse(
        String from,
        String to,
        String searchType,
        List<String> route,
        List<String> flightIds,
        Double totalDistanceKm,
        Integer totalDurationMinutes,
        Double totalPrice,
        Integer transfers) {

    /** Boxed types so a missing field arrives as null and can be detected here. */
    @JsonIgnore // otherwise Jackson treats isComplete() as a "complete" property and adds it to the JSON
    public boolean isComplete() {
        return from != null && to != null && route != null && !route.isEmpty()
                && totalDistanceKm != null && totalDurationMinutes != null && totalPrice != null && transfers != null;
    }

    @JsonIgnore
    public String summary() {
        return String.join(">", route) + " | " + totalDistanceKm + " km | " + totalDurationMinutes + " min | "
                + totalPrice + " | " + transfers + " transfer(s)";
    }
}
