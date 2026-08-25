package com.expensetracker.unitTests;

import com.expensetracker.auth.AuthService;
import com.expensetracker.auth.JwtUtil;
import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.LoginRequest;
import com.expensetracker.dto.RefreshRequest;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password-hash";
    private static final String fakeAccessToken = "fake-access-token";
    private static final String fakeRefreshToken = "fake-refresh-token";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    @Nested
    class Register {

        @Test
        void shouldRegisterNewUserAndReturnTokens() {
            RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateAccessToken(any(User.class))).thenReturn(fakeAccessToken);
            when(jwtUtil.generateRefreshToken(any(User.class))).thenReturn(fakeRefreshToken);

            AuthResponse response = authService.register(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
            assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(response.accessToken()).isEqualTo(fakeAccessToken);
            assertThat(response.refreshToken()).isEqualTo(fakeRefreshToken);
            assertThat(response.user().email()).isEqualTo(EMAIL);
        }

        @Test
        void shouldThrowWhenEmailAlreadyRegistered() {
            RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(new User()));

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(jwtUtil);
        }

        @Test
        void shouldStoreEncodedPasswordNotRawPassword() {
            RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateAccessToken(any(User.class))).thenReturn(fakeAccessToken);
            when(jwtUtil.generateRefreshToken(any(User.class))).thenReturn(fakeRefreshToken);

            authService.register(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
        }
    }

    @Nested
    class Login {

        @Test
        void shouldLoginWithValidCredentialsAndReturnTokens() {
            User existingUser = new User();
            existingUser.setEmail(EMAIL);
            existingUser.setPassword(ENCODED_PASSWORD);

            LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateAccessToken(existingUser)).thenReturn(fakeAccessToken);
            when(jwtUtil.generateRefreshToken(existingUser)).thenReturn(fakeRefreshToken);

            AuthResponse response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo(fakeAccessToken);
            assertThat(response.refreshToken()).isEqualTo(fakeRefreshToken);
            assertThat(response.user().email()).isEqualTo(EMAIL);
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid credentials");

            verifyNoInteractions(jwtUtil);
        }

        @Test
        void shouldThrowWhenPasswordDoesNotMatch() {
            User existingUser = new User();
            String wrongPassword = "wrong-password";
            existingUser.setEmail(EMAIL);
            existingUser.setPassword(ENCODED_PASSWORD);

            LoginRequest request = new LoginRequest(EMAIL, wrongPassword);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(wrongPassword, ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid credentials");

            verifyNoInteractions(jwtUtil);
        }
    }

    @Nested
    class Refresh {

        @Test
        void shouldIssueNewAccessTokenForValidRefreshToken() throws Exception {
            User existingUser = new User();
            existingUser.setEmail(EMAIL);

            RefreshRequest request = new RefreshRequest(fakeRefreshToken);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(EMAIL)
                    .claim("type", "refresh")
                    .build();

            when(jwtUtil.isValid(fakeRefreshToken)).thenReturn(true);
            when(jwtUtil.parseClaims(fakeRefreshToken)).thenReturn(claims);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));
            when(jwtUtil.generateAccessToken(existingUser)).thenReturn("new-access-token");

            AuthResponse response = authService.refresh(request);

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo(fakeRefreshToken); // not rotated
            assertThat(response.user().email()).isEqualTo(EMAIL);

            // Regression guard: refresh() must issue an ACCESS token, not another refresh token
            verify(jwtUtil).generateAccessToken(existingUser);
            verify(jwtUtil, never()).generateRefreshToken(any(User.class));
        }

        @Test
        void shouldThrowWhenRefreshTokenIsInvalid() {
            RefreshRequest request = new RefreshRequest("bad-token");
            when(jwtUtil.isValid("bad-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid or expired refresh token");

            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        void shouldThrowWhenTokenIsAccessTokenNotRefreshToken() throws Exception {
            RefreshRequest request = new RefreshRequest(fakeAccessToken);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(EMAIL)
                    .claim("type", "access") // wrong type
                    .build();

            when(jwtUtil.isValid(fakeAccessToken)).thenReturn(true);
            when(jwtUtil.parseClaims(fakeAccessToken)).thenReturn(claims);

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(RuntimeException.class);

            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        void shouldThrowWhenUserNoLongerExists() throws Exception {
            RefreshRequest request = new RefreshRequest(fakeRefreshToken);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(EMAIL)
                    .claim("type", "refresh")
                    .build();

            when(jwtUtil.isValid(fakeRefreshToken)).thenReturn(true);
            when(jwtUtil.parseClaims(fakeRefreshToken)).thenReturn(claims);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid or expired refresh token");

            verify(jwtUtil, never()).generateAccessToken(any(User.class));
        }
    }
}