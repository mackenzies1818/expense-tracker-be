package com.expensetracker.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {}
