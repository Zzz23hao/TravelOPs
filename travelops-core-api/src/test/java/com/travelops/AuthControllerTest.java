package com.travelops;

import com.travelops.controller.AuthController;
import com.travelops.controller.UserController;
import com.travelops.dto.AuthResponse;
import com.travelops.dto.UserResponse;
import com.travelops.security.JwtService;
import com.travelops.security.SecurityConfig;
import com.travelops.service.AuthService;
import com.travelops.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real SecurityConfig and JWT filter in front of the controllers; services are mocked.
 */
@WebMvcTest({AuthController.class, UserController.class})
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "jwt.expiration-minutes=60"})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @Test
    void registerIsPublicAndReturns201() throws Exception {
        when(authService.register(any())).thenReturn(
                new UserResponse(1L, "alice@example.com", "Alice", "USER", Instant.now()));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "alice@example.com", "password": "Password123!", "name": "Alice"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    void registerWithInvalidEmailAndShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short", "name": "Alice"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "email: must be a well-formed email address; password: size must be between 8 and 72"));
        verifyNoInteractions(authService);
    }

    @Test
    void loginIsPublicAndReturnsBearerToken() throws Exception {
        when(authService.login(any())).thenReturn(AuthResponse.bearer("token-value"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "alice@example.com", "password": "Password123!"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("token-value"))
                .andExpect(jsonPath("$.token_type").value("bearer"));
    }

    @Test
    void meWithoutTokenReturns401WithTypedErrorBody() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verifyNoInteractions(userService);
    }

    @Test
    void meWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void meWithValidTokenReturnsTheTokenSubjectsProfile() throws Exception {
        when(userService.getProfile(42L)).thenReturn(
                new UserResponse(42L, "alice@example.com", "Alice", "USER", Instant.now()));

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
        verify(userService).getProfile(42L);
    }
}
