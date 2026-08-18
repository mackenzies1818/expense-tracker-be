package com.expensetracker.dto;

public record LoginRequest(
        String email,
        String password
) {}
