package com.travelops.dto;

public record AuthResponse(String accessToken, String tokenType) {

    public static AuthResponse bearer(String token) {
        return new AuthResponse(token, "bearer");
    }
}
