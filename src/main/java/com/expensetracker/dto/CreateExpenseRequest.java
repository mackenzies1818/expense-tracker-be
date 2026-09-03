package com.expensetracker.dto;

import com.expensetracker.util.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateExpenseRequest(
        String description,
        BigDecimal amount,
        UUID expenseToken,
        Instant expenseTime,
        ExpenseCategory category
) {}


