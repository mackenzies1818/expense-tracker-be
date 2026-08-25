package com.expensetracker.integrationTests;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.RegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate; // new package
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class AbstractIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "integration-test-secret-key-at-least-32-bytes-long!!";

    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("expenses_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("JWT_SECRET", () -> TEST_JWT_SECRET);
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected String registerAndGetToken(String email, String password) {
        RegisterRequest request = new RegisterRequest(email, password);

        AuthResponse response = restTemplate.postForObject(
                "/api/auth/register",
                request,
                AuthResponse.class
        );

        return response.accessToken();
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}