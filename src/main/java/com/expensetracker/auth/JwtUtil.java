package com.expensetracker.auth;

import com.expensetracker.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;

@Component
public class JwtUtil {

    private final String secret;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs
    ) {
        this.secret = secret;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(User user) {
        return generateToken(user, "access", accessTokenExpirationMs);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, "refresh", refreshTokenExpirationMs);
    }

    private String generateToken(User user, String type, long expiryMs) {
        try {
            Date now = new Date();
            Date expiration = new Date(now.getTime() + expiryMs);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getEmail())
                    .claim("type", type)
                    .issueTime(now)
                    .expirationTime(expiration)
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(new MACSigner(secret));

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Could not generate JWT", e);
        }
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            JWTClaimsSet claims = parseClaims(token);
            Date expiration = claims.getExpirationTime();
            return expiration != null && expiration.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public JWTClaimsSet parseClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret);

            if (!signedJWT.verify(verifier)) {
                throw new RuntimeException("Invalid JWT signature");
            }

            return signedJWT.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw new RuntimeException("Invalid JWT", e);
        }
    }
}