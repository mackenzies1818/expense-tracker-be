package com.expensetracker.controller;

import com.expensetracker.dto.CreateExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.UpdateExpenseRequest;
import com.expensetracker.model.Expense;
import com.expensetracker.services.ExpenseService;
import com.expensetracker.util.ExpenseCategory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public List<ExpenseResponse> getExpenses(
            Authentication authentication,
            @RequestParam(required = false) ExpenseCategory expenseCategory
    ) {
        return expenseService.getExpensesForUser(authentication.getName(), expenseCategory);
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