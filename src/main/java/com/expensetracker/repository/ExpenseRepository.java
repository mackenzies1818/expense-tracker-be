package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import com.expensetracker.util.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserId(Long userId);

    List<Expense> findByUserIdAndCategory(
            Long userId,
            ExpenseCategory category
    );
    Optional<Expense> findByUserIdAndToken(Long userId, UUID token);

    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);
}