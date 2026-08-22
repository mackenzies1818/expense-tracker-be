package com.expensetracker.integrationTests;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.ErrorResponse;
import com.expensetracker.dto.LoginRequest;
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
        assertThat(response.getBody().token()).isNotBlank();
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
        String token = loginResponse.getBody().token();
        assertThat(token).isNotBlank();
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
}