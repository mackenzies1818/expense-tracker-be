package com.expensetracker.controller;

import com.expensetracker.dto.*;
import com.expensetracker.model.Expense;
import com.expensetracker.services.ExpenseService;
import com.expensetracker.util.ExpenseCategory;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@RequestBody CreateExpenseRequest expense, Authentication authentication) {
        System.out.println("creating expense");
        return expenseService.createExpense(expense, authentication.getName());
    }

    @GetMapping("/{expenseToken}")
    @ResponseStatus(HttpStatus.OK)
    public ExpenseResponse getExpense(
            @PathVariable UUID expenseToken,
            Authentication authentication
    ) {
        return expenseService.getExpense(expenseToken, authentication.getName());
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public PagedResponse<ExpenseResponse> getExpenses(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDateTime endDate,
            @RequestParam(required = false) List<ExpenseCategory> expenseCategory,
            @RequestParam(defaultValue = "expenseTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        ExpenseFilter filter = new ExpenseFilter();
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setCategories(expenseCategory);
        filter.setSortBy(sortBy);
        filter.setSortOrder(Sort.Direction.fromString(sortOrder));
        filter.setPage(page);
        filter.setPageSize(pageSize);
        return expenseService.getExpensesForUser(
                authentication.getName(), filter
        );
    }

    @PutMapping("/{expenseToken}")
    @ResponseStatus(HttpStatus.OK)
    public ExpenseResponse updateExpense(
            @PathVariable UUID expenseToken,
            @RequestBody UpdateExpenseRequest expense,
            Authentication authentication
    ) {
        return expenseService.updateExpense(expenseToken, expense, authentication.getName());
    }

    @DeleteMapping("/{expenseToken}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(
            @PathVariable UUID expenseToken,
            Authentication authentication
    ) {
        expenseService.deleteExpense(expenseToken, authentication.getName());
    }
}