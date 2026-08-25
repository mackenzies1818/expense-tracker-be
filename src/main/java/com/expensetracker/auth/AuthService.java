package com.expensetracker.auth;

import com.expensetracker.dto.*;
import com.expensetracker.exceptions.DuplicateResourceException;
import com.expensetracker.exceptions.InvalidCredentialsException;
import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateResourceException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        return mapDatatoAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        return mapDatatoAuthResponse(user, accessToken, refreshToken);
    }

    private AuthResponse mapDatatoAuthResponse(User user, String accessToken, String refreshToken) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                new UserResponse(user.getId(), user.getEmail())
        );
    }

    public AuthResponse refresh(RefreshRequest request) {
        String refreshToken = request.token();

        // 1. Validate signature + expiry of the refresh token itself
        if (!jwtUtil.isValid(refreshToken)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        try {
            // 2. Make sure it's actually a refresh token, not an access token
            JWTClaimsSet claims = jwtUtil.parseClaims(refreshToken);
            String type = (String) claims.getClaim("type");
            if (!"refresh".equals(type)) {
                throw new InvalidCredentialsException("Token is not a refresh token");
            }

            // 3. Issue a new access token
            String username = claims.getSubject();
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

            String newAccessToken = jwtUtil.generateAccessToken(user); // fixed: was generateRefreshToken
            return mapDatatoAuthResponse(user, newAccessToken, refreshToken);

        } catch (RuntimeException e) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }
    }
}