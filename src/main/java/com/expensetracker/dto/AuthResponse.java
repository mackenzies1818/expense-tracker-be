package com.expensetracker.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {}
