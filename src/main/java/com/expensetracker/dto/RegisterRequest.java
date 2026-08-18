package com.expensetracker.dto;

public record RegisterRequest(
        String email,
        String password
) {}
