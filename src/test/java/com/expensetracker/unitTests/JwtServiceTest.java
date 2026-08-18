package com.expensetracker.unitTests;

import com.expensetracker.auth.JwtService;
import com.expensetracker.model.User;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901"; // 32+ chars for HS256
    private JwtService jwtService;
    private User user;
    private static final String testEmail = "user@example.com";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
        user = new User();
        user.setEmail(testEmail);
    }

    @Test
    void shouldGenerateValidSignedJwtWithEmailAsSubject() throws Exception {
        String token = jwtService.generateToken(user);

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo(testEmail);
        assertThat(signedJWT.getJWTClaimsSet().getExpirationTime()).isAfter(new java.util.Date());
        assertThat(signedJWT.getJWTClaimsSet().getIssueTime()).isBeforeOrEqualTo(new java.util.Date());
    }

    @Test
    void shouldExtractEmailFromValidToken() {
        String token = jwtService.generateToken(user);

        String email = jwtService.extractEmail(token);

        assertThat(email).isEqualTo(testEmail);
    }

    @Test
    void shouldThrowWhenExtractingEmailFromMalformedToken() {
        assertThatThrownBy(() -> jwtService.extractEmail("not-a-jwt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid JWT");
    }

    @Test
    void shouldGenerateDifferentTokensForSameUserOnSuccessiveCalls() throws InterruptedException {
        String token1 = jwtService.generateToken(user);
        Thread.sleep(1000); // ensure issueTime (second precision) differs
        String token2 = jwtService.generateToken(user);

        assertThat(token1).isNotEqualTo(token2);
    }
}
