package com.expensetracker.unitTests;

import com.expensetracker.auth.AuthService;
import com.expensetracker.auth.JwtService;
import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.LoginRequest;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password-hash";
    private static final String fakeJwtToken = "fake-jwt-token";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Nested
    class Register {

        @Test
        void shouldRegisterNewUserAndReturnToken() {
            RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtService.generateToken(any(User.class))).thenReturn(fakeJwtToken);

            AuthResponse response = authService.register(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
            assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(response.token()).isEqualTo(fakeJwtToken);
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
            verifyNoInteractions(jwtService);
        }

        @Test
        void shouldStoreEncodedPasswordNotRawPassword() {
            RegisterRequest request = new RegisterRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtService.generateToken(any(User.class))).thenReturn("token");

            authService.register(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
        }
    }

    @Nested
    class Login {

        @Test
        void shouldLoginWithValidCredentialsAndReturnToken() {
            User existingUser = new User();
            existingUser.setEmail(EMAIL);
            existingUser.setPassword(ENCODED_PASSWORD);

            LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtService.generateToken(existingUser)).thenReturn(fakeJwtToken);

            AuthResponse response = authService.login(request);

            assertThat(response.token()).isEqualTo(fakeJwtToken);
            assertThat(response.user().email()).isEqualTo(EMAIL);
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid credentials");

            verifyNoInteractions(jwtService);
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

            verifyNoInteractions(jwtService);
        }
    }
}
