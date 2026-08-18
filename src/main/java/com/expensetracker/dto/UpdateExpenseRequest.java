package com.expensetracker.dto;

import com.expensetracker.util.ExpenseCategory;

import java.math.BigDecimal;

public record UpdateExpenseRequest(
        String description,
        BigDecimal amount,
        ExpenseCategory category
) {}


