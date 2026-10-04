package com.expensetracker.dto;

import com.expensetracker.util.ExpenseCategory;
import lombok.Data;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExpenseFilter {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<ExpenseCategory> categories;
    private String sortBy = "expenseTime";
    private Sort.Direction sortOrder = Sort.Direction.DESC;
    private int page = 0;
    private int pageSize = 10;

}
