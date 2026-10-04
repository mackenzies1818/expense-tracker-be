package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import com.expensetracker.util.ExpenseCategory;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class ExpenseSpecifications {

    public static Specification<Expense> hasUserId(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Expense> dateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start != null && end != null) {
                return cb.between(root.get("expenseTime"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("expenseTime"), start);
            } else if (end != null) {
                return cb.lessThanOrEqualTo(root.get("expenseTime"), end);
            }
            return cb.conjunction(); // no-op filter
        };
    }

    public static Specification<Expense> inCategories(List<ExpenseCategory> categories) {
        return (root, query, cb) -> {
            if (categories == null || categories.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("category").in(categories);
        };
    }
}
