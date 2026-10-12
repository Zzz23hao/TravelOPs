package com.travelops;

import com.travelops.dto.AuthResponse;
import com.travelops.dto.LoginRequest;
import com.travelops.dto.RegisterRequest;
import com.travelops.dto.UserResponse;
import com.travelops.entity.User;
import com.travelops.exception.ApiException;
import com.travelops.repository.UserRepository;
import com.travelops.security.JwtService;
import com.travelops.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Password123!";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService("test-secret-that-is-at-least-32-bytes-long", Duration.ofMinutes(60), Clock.systemUTC());

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerSucceedsAndNormalizesEmail() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest(" Alice@Example.com ", PASSWORD, "Alice"));

        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void registerStoresBcryptHashNotThePlainPassword() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("alice@example.com", PASSWORD, "Alice"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2a$10$");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void registerWithTakenEmailReturns409AndSavesNothing() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice@example.com", PASSWORD, "Alice")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateRegistrationIsMappedTo409() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice@example.com", PASSWORD, "Alice")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginWithCorrectPasswordReturnsTokenForThatUser() {
        User alice = existingUser(7L, "alice@example.com", PASSWORD);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        AuthResponse response = authService.login(new LoginRequest("ALICE@example.com", PASSWORD));

        assertThat(response.tokenType()).isEqualTo("bearer");
        assertThat(jwtService.parseUserId(response.accessToken())).contains(7L);
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        User alice = existingUser(7L, "alice@example.com", PASSWORD);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void loginWithUnknownEmailGivesTheSameErrorAsWrongPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(api.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    private User existingUser(Long id, String email, String rawPassword) {
        User user = new User(email, passwordEncoder.encode(rawPassword), "Alice");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
