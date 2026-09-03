package com.expensetracker.dto;

import com.expensetracker.util.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateExpenseRequest(
        String description,
        BigDecimal amount,
        ExpenseCategory category,
        Instant expenseTime
) {}


