package com.travelops;

import com.travelops.security.JwtService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private static final Duration TTL = Duration.ofMinutes(60);

    private final JwtService jwtService = new JwtService(SECRET, TTL, Clock.systemUTC());

    @Test
    void tokenRoundTripsUserId() {
        String token = jwtService.generateToken(42L);

        assertThat(jwtService.parseUserId(token)).contains(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String expired = new JwtService(SECRET, TTL, twoHoursAgo).generateToken(42L);

        assertThat(jwtService.parseUserId(expired)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = new JwtService("another-secret-that-is-also-32-bytes-long", TTL, Clock.systemUTC())
                .generateToken(42L);

        assertThat(jwtService.parseUserId(foreign)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = jwtService.generateToken(42L).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThat(jwtService.parseUserId(tampered)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.parseUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", TTL, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
