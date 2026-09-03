package com.expensetracker.dto;

import com.expensetracker.util.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExpenseResponse (
        UUID expenseToken,
        String description,
        BigDecimal amount,
        ExpenseCategory category,
        Instant expenseTime,
        Instant createdTime,
        Instant updatedTime
) {}
