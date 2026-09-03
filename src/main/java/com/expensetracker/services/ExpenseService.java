package com.expensetracker.services;

import com.expensetracker.dto.CreateExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.UpdateExpenseRequest;
import com.expensetracker.exceptions.ResourceNotFoundException;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.util.ExpenseCategory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public ExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    public ExpenseResponse createExpense(CreateExpenseRequest request, String email) {
        System.out.println("creating expense with email: "+email);
        System.out.println("creating expense with amount: "+request.amount());
        User user = getUser(email);

        Expense expense = new Expense();

        expense.setDescription(request.description());
        expense.setAmount(request.amount());
        expense.setUser(user);
        expense.setCategory(request.category());

        UUID token = request.expenseToken();

        if (token == null) {
            token = UUID.randomUUID();
        }
        expense.setToken(token);

        Instant expenseTime = request.expenseTime();
        if (expenseTime == null) {
            expenseTime = Instant.now();
        }
        expense.setExpenseTime(expenseTime);
        System.out.println("creating expense with token: "+expense.getToken());

        return mapExpensetoExpenseResponse(expenseRepository.save(expense));
    }

    public ExpenseResponse getExpense(UUID expenseToken, String email) {
        User user = getUser(email);
        return mapExpensetoExpenseResponse(expenseRepository.findByUserIdAndToken(user.getId(), expenseToken).orElseThrow(() -> new ResourceNotFoundException("Expense not found")));
    }


    public List<ExpenseResponse> getExpensesForUser(String email, ExpenseCategory expenseCategory) {
        User user = getUser(email);
        if (expenseCategory != null) {
            return expenseRepository.findByUserIdAndCategory(
                    user.getId(),
                    expenseCategory)
                    .stream()
                    .map(this::mapExpensetoExpenseResponse)
                    .toList();
        }
        return expenseRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapExpensetoExpenseResponse)
                .toList();
    }

    public ExpenseResponse updateExpense(UUID expenseToken, UpdateExpenseRequest request, String email) {
        User user = getUser(email);

        Expense expense = expenseRepository.findByUserIdAndToken(user.getId(), expenseToken).orElseThrow(() -> new ResourceNotFoundException("Expense not found"));

        if (!request.description().isEmpty()) {
            expense.setDescription(request.description());
        }
        if (!request.category().toString().isEmpty()) {
            expense.setCategory(request.category());
        }
        if (request.amount() != null) {
            expense.setAmount(request.amount());
        }
        if (request.expenseTime() != null) {
            expense.setExpenseTime(request.expenseTime());
        }
        return  mapExpensetoExpenseResponse(expenseRepository.save(expense));

    }

    public void deleteExpense(UUID expenseToken, String email) {
        User user = getUser(email);

        Expense expense = expenseRepository.findByUserIdAndToken(user.getId(), expenseToken).orElseThrow(() -> new ResourceNotFoundException("Expense not found"));

        expenseRepository.delete(expense);
    }

    private ExpenseResponse mapExpensetoExpenseResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getToken(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCategory(),
                expense.getExpenseTime(),
                expense.getCreatedTime(),
                expense.getUpdatedTime()
        );
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }



}