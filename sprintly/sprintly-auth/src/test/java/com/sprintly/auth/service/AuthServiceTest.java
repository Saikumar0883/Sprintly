package com.sprintly.auth.service;

import com.sprintly.auth.dto.LoginRequest;
import com.sprintly.auth.dto.RegisterRequest;
import com.sprintly.auth.dto.AuthResponse;
import com.sprintly.auth.entity.RefreshToken;
import com.sprintly.auth.repository.RefreshTokenRepository;
import com.sprintly.common.enums.UserRole;
import com.sprintly.common.exception.BadRequestException;
import com.sprintly.common.exception.UnauthorizedException;
import com.sprintly.user.entity.User;
import com.sprintly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ─────────────────────────────────────────────────────────────────
 *  AuthServiceTest
 * ─────────────────────────────────────────────────────────────────
 *  Unit tests for AuthService using Mockito.
 *
 *  All dependencies are mocked — no Spring context, no DB.
 *  Tests run in milliseconds and cover happy paths + edge cases.
 *
 *  Naming convention: methodName_scenario_expectedBehavior()
 * ─────────────────────────────────────────────────────────────────
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository         userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtService             jwtService;
    @Mock private AuthenticationManager  authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        // Reusable test user across all test cases
        mockUser = User.builder()
                .id(1L)
                .name("Ravi Kumar")
                .email("ravi@sprintly.com")
                .password("$2a$12$hashedpassword")
                .role(UserRole.ROLE_DEVELOPER)
                .enabled(true)
                .build();
    }

    // ── Register Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("register: happy path — creates user and returns tokens")
    void register_validRequest_returnsAuthResponse() {
        // GIVEN
        RegisterRequest request = new RegisterRequest();
        request.setName("Ravi Kumar");
        request.setEmail("ravi@sprintly.com");
        request.setPassword("MyPass@123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(jwtService.generateAccessToken(any(), anyLong(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiryMs()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // WHEN
        AuthResponse response = authService.register(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getEmail()).isEqualTo("ravi@sprintly.com");
        assertThat(response.getRole()).isEqualTo("ROLE_DEVELOPER");

        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("register: duplicate email — throws BadRequestException")
    void register_duplicateEmail_throwsBadRequestException() {
        // GIVEN
        RegisterRequest request = new RegisterRequest();
        request.setEmail("ravi@sprintly.com");
        request.setName("Ravi");
        request.setPassword("pass1234");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // WHEN / THEN
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ravi@sprintly.com");

        // Should NOT save when email is duplicate
        verify(userRepository, never()).save(any());
    }

    // ── Login Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("login: valid credentials — returns tokens")
    void login_validCredentials_returnsAuthResponse() {
        // GIVEN
        LoginRequest request = new LoginRequest();
        request.setEmail("ravi@sprintly.com");
        request.setPassword("MyPass@123");

        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("ravi@sprintly.com", null)
        );
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(mockUser));
        when(jwtService.generateAccessToken(any(), anyLong(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiryMs()).thenReturn(900000L);
        when(jwtService.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // WHEN
        AuthResponse response = authService.login(request);

        // THEN
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("login: wrong password — throws UnauthorizedException")
    void login_wrongPassword_throwsUnauthorizedException() {
        // GIVEN
        LoginRequest request = new LoginRequest();
        request.setEmail("ravi@sprintly.com");
        request.setPassword("wrongpass");

        // AuthenticationManager throws BadCredentialsException on wrong password
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // WHEN / THEN
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    // ── Logout Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("logout: valid user — revokes all tokens")
    void logout_validUser_revokesAllTokens() {
        // GIVEN
        when(userRepository.findByEmail("ravi@sprintly.com"))
                .thenReturn(Optional.of(mockUser));

        // WHEN
        authService.logout("ravi@sprintly.com");

        // THEN: all refresh tokens for the user must be deleted
        verify(refreshTokenRepository).deleteAllByUser(mockUser);
    }
}
