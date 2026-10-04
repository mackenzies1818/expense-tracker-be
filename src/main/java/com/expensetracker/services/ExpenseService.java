package com.expensetracker.services;

import com.expensetracker.dto.*;
import com.expensetracker.exceptions.InvalidFilterException;
import com.expensetracker.exceptions.ResourceNotFoundException;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.ExpenseSpecifications;
import com.expensetracker.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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


    public PagedResponse<ExpenseResponse> getExpensesForUser(String email, ExpenseFilter filter) {
        User user = getUser(email);
        if(filter.getEndDate() != null  || filter.getStartDate() != null) {
            validateDateRange(filter.getStartDate(), filter.getEndDate());
        }
        Sort sort = Sort.by(filter.getSortOrder(), validateSortField(filter.getSortBy()));
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getPageSize(), sort);
        Specification<Expense> spec = Specification
                .where(ExpenseSpecifications.hasUserId(user.getId()))
                .and(ExpenseSpecifications.dateBetween(filter.getStartDate(), filter.getEndDate()))
                .and(ExpenseSpecifications.inCategories(filter.getCategories()));

        Page<Expense> result = expenseRepository.findAll(spec, pageable);

        List<ExpenseResponse> dtos = result.getContent().stream()
                .map(this::mapExpensetoExpenseResponse)
                .toList();

        return new PagedResponse<>(
                dtos,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private String validateSortField(String sortBy) {
        Set<String> allowed = Set.of("expenseTime", "amount", "expenseCategory");
        if (!allowed.contains(sortBy)) {
            throw new InvalidFilterException(
                    "Invalid sortBy field: '" + sortBy + "'. Allowed values: " + allowed
            );        }
        return sortBy;
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidFilterException("startDate must be before endDate");
        }
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