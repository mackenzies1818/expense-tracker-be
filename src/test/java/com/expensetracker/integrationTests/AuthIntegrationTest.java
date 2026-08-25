package com.expensetracker.integrationTests;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.ErrorResponse;
import com.expensetracker.dto.LoginRequest;
import com.expensetracker.dto.RefreshRequest;
import com.expensetracker.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldRegisterNewUserAndReturnUsableToken() {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "Password123!");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().user()).isNotNull();
        assertThat(response.getBody().user().id()).isNotNull();
        assertThat(response.getBody().user().email()).isEqualTo(email);
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() {
        String email = uniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "Password123!");
        restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/register", request, ErrorResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Email already registered");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldLoginWithValidCredentialsAndTokenShouldWorkOnProtectedEndpoint() {
        String email = uniqueEmail();
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(email, "Password123!"), AuthResponse.class);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().accessToken()).isNotNull();
        assertThat(loginResponse.getBody().refreshToken()).isNotNull();
        String token = loginResponse.getBody().accessToken();
        assertThat(loginResponse.getBody().user()).isNotNull();
        assertThat(loginResponse.getBody().user().id()).isNotNull();
        assertThat(loginResponse.getBody().user().email()).isEqualTo(email);

        // Prove the token actually authenticates against the real filter chain,
        // not just that login returned something non-null.
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        String email = uniqueEmail();
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(email, "Password123!"), AuthResponse.class);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "WrongPassword!"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void shouldRejectLoginForNonexistentUser() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(uniqueEmail(), "whatever"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void shouldRefreshAccessTokenAndNewTokenShouldWorkOnProtectedEndpoint() {
        String email = uniqueEmail();
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(email, "Password123!"), AuthResponse.class);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);
        String refreshToken = loginResponse.getBody().refreshToken();

        ResponseEntity<AuthResponse> refreshResponse = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(refreshToken), AuthResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();

        // This is the exact end-to-end path that was previously broken:
        // a token issued by /refresh must actually authenticate on a protected endpoint.
        String newAccessToken = refreshResponse.getBody().accessToken();
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(authHeaders(newAccessToken)), String.class);
        assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldRejectRefreshWithGarbageToken() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest("not-a-real-token"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectRefreshUsingAnAccessTokenInsteadOfARefreshToken() {
        String email = uniqueEmail();
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(email, "Password123!"), AuthResponse.class);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(email, "Password123!"), AuthResponse.class);
        String accessToken = loginResponse.getBody().accessToken();

        // Using the access token where a refresh token is expected should be rejected,
        // since it carries type=access, not type=refresh.
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/refresh", new RefreshRequest(accessToken), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}