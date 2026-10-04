package com.expensetracker.unitTests;

import com.expensetracker.dto.*;
import com.expensetracker.exceptions.InvalidFilterException;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.services.ExpenseService;
import com.expensetracker.util.ExpenseCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    private ExpenseService expenseService;

    private static final String EMAIL = "user@example.com";
    private static final Long USERID = 1L;
    private User user;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, userRepository);
        user = new User();
        user.setId(USERID);
        user.setEmail(EMAIL);
    }

    // ---------- createExpense ----------

    @Nested
    class CreateExpense {

        @Test
        void shouldCreateExpenseWithGeneratedTokenWhenNoneProvided() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            String description = "Weekly Groceries at Walmart";
            BigDecimal amount = BigDecimal.valueOf(50.00);
            ExpenseCategory category = ExpenseCategory.GROCERIES;
            Instant expenseTime = Instant.now();
            CreateExpenseRequest request = new CreateExpenseRequest(
                    description, amount, null, expenseTime, category);

            when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

            ExpenseResponse response = expenseService.createExpense(request, EMAIL);

            ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
            verify(expenseRepository).save(captor.capture());

            Expense saved = captor.getValue();
            assertThat(saved.getToken()).isNotNull();
            assertThat(saved.getDescription()).isEqualTo(description);
            assertThat(saved.getAmount()).isEqualByComparingTo(amount);
            assertThat(saved.getUser()).isEqualTo(user);
            assertThat(response.description()).isEqualTo(description);
            assertThat(response.amount()).isEqualByComparingTo(amount);
            assertThat(response.category()).isEqualTo(category);
            assertThat(response.expenseTime()).isEqualTo(expenseTime);
        }

        @Test
        void shouldCreateExpenseUsingProvidedToken() {
            UUID providedToken = UUID.randomUUID();
            String description = "Rent";
            BigDecimal amount = BigDecimal.valueOf(1200);
            ExpenseCategory category = ExpenseCategory.HOUSING;
            Instant expenseTime = Instant.now();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            CreateExpenseRequest request = new CreateExpenseRequest(
                    description, amount, providedToken, expenseTime, category);

            when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

            expenseService.createExpense(request, EMAIL);

            ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
            verify(expenseRepository).save(captor.capture());
            assertThat(captor.getValue().getToken()).isEqualTo(providedToken);
            assertThat(captor.getValue().getDescription()).isEqualTo(description);
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(amount);
            assertThat(captor.getValue().getCategory()).isEqualTo(category);
            assertThat(captor.getValue().getExpenseTime()).isEqualTo(expenseTime);
        }

        @Test
        void shouldThrowWhenUserNotFoundOnCreate() {
            String description = "Dinner at Jack Astors";
            BigDecimal amount = BigDecimal.valueOf(10);
            ExpenseCategory category = ExpenseCategory.EATING_OUT;
            Instant expenseTime = Instant.now();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            CreateExpenseRequest request = new CreateExpenseRequest(
                    description, amount, null, expenseTime, category);

            assertThatThrownBy(() -> expenseService.createExpense(request, EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");

            verifyNoInteractions(expenseRepository);
        }
    }

    // ---------- getExpense ----------

    @Nested
    class GetExpense {

        @Test
        void shouldReturnExpenseWhenFound() {
            UUID token = UUID.randomUUID();
            String description = "Coffee";
            BigDecimal amount = BigDecimal.valueOf(4.5);
            ExpenseCategory category = ExpenseCategory.EATING_OUT;
            Instant expenseTime = Instant.now();
            Expense expense = buildExpense(token, description, amount, expenseTime, category);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.of(expense));

            ExpenseResponse response = expenseService.getExpense(token, EMAIL);

            assertThat(response.expenseToken()).isEqualTo(token);
            assertThat(response.description()).isEqualTo(description);
            assertThat(response.amount()).isEqualTo(amount);
            assertThat(response.category()).isEqualTo(category);
            assertThat(response.expenseTime()).isEqualTo(expenseTime);
        }

        @Test
        void shouldThrowWhenExpenseNotFound() {
            UUID token = UUID.randomUUID();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.getExpense(token, EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Expense not found");
        }

        @Test
        void shouldThrowWhenUserNotFoundOnGet() {
            UUID token = UUID.randomUUID();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.getExpense(token, EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");

            verifyNoInteractions(expenseRepository);
        }
    }

    // ---------- getExpensesForUser ----------

    @Nested
    class GetExpenses {

        @Test
        void shouldReturnAllExpensesWhenNoFiltersProvided() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            Expense e1 = buildExpense(UUID.randomUUID(), "B", BigDecimal.ONE, Instant.now(), ExpenseCategory.EATING_OUT);
            Expense e2 = buildExpense(UUID.randomUUID(), "A", BigDecimal.TEN, Instant.now(), ExpenseCategory.HOUSING);
            Page<Expense> page = new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 10), 2);

            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            PagedResponse<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, new ExpenseFilter());

            assertThat(result.getData()).hasSize(2);
            assertThat(result.getTotalItems()).isEqualTo(2);
        }

        @Test
        void shouldFilterByDateRange() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            ExpenseFilter filter = new ExpenseFilter();
            filter.setStartDate(LocalDateTime.of(2026, 8, 1, 12, 12));
            filter.setEndDate(LocalDateTime.of(2026, 8, 31, 12, 12));

            Expense e1 = buildExpense(UUID.randomUUID(), "Groceries", BigDecimal.TEN,
                    Instant.parse("2026-08-15T10:00:00Z"), ExpenseCategory.EATING_OUT);
            Page<Expense> page = new PageImpl<>(List.of(e1), PageRequest.of(0, 10), 1);

            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            PagedResponse<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, filter);

            assertThat(result.getData()).hasSize(1);
        }

        @Test
        void shouldFilterByMultipleCategories() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            ExpenseFilter filter = new ExpenseFilter();
            filter.setCategories(List.of(ExpenseCategory.EATING_OUT, ExpenseCategory.HOUSING));

            Expense e1 = buildExpense(UUID.randomUUID(), "Lunch", BigDecimal.TEN, Instant.now(), ExpenseCategory.EATING_OUT);
            Page<Expense> page = new PageImpl<>(List.of(e1), PageRequest.of(0, 10), 1);

            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

            PagedResponse<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, filter);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).category()).isEqualTo(ExpenseCategory.EATING_OUT);
        }

        @Test
        void shouldApplySortAndPagination() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            ExpenseFilter filter = new ExpenseFilter();
            filter.setSortBy("amount");
            filter.setSortOrder(Sort.Direction.ASC);
            filter.setPage(1);
            filter.setPageSize(5);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            when(expenseRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

            expenseService.getExpensesForUser(EMAIL, filter);

            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(1);
            assertThat(captured.getPageSize()).isEqualTo(5);
            assertThat(captured.getSort().getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        void shouldReturnEmptyPagedResponseWhenNoExpenses() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

            PagedResponse<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, new ExpenseFilter());

            assertThat(result.getData()).isEmpty();
        }

        @Test
        void shouldThrowWhenSortFieldNotWhitelisted() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            ExpenseFilter filter = new ExpenseFilter();
            filter.setSortBy("someRandomField");

            assertThatThrownBy(() -> expenseService.getExpensesForUser(EMAIL, filter))
                    .isInstanceOf(InvalidFilterException.class);
        }
    }

    // ---------- updateExpense ----------

    @Nested
    class UpdateExpense {

        @Test
        void shouldUpdateAllFieldsWhenAllProvided() {
            UUID token = UUID.randomUUID();
            String oldDescription = "Old desc";
            String newDescription = "New desc";
            BigDecimal oldAmount = BigDecimal.valueOf(4.5);
            BigDecimal newAmount = BigDecimal.valueOf(10);
            ExpenseCategory oldCategory = ExpenseCategory.EATING_OUT;
            ExpenseCategory newCategory = ExpenseCategory.HOUSING;
            Instant expenseTime = Instant.now();
            Instant newExpenseTime = expenseTime.plus(Duration.ofMinutes(10));
            Expense existing = buildExpense(token, oldDescription, oldAmount, expenseTime, oldCategory);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.of(existing));
            when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateExpenseRequest request = new UpdateExpenseRequest(
                    newDescription, newAmount, newCategory, newExpenseTime);

            ExpenseResponse response = expenseService.updateExpense(token, request, EMAIL);

            assertThat(response.description()).isEqualTo(newDescription);
            assertThat(response.amount()).isEqualByComparingTo(newAmount);
            assertThat(response.category()).isEqualTo(newCategory);
            assertThat(response.expenseTime()).isEqualTo(newExpenseTime);
        }

        @Test
        void shouldKeepExistingAmountWhenNullInRequest() {
            UUID token = UUID.randomUUID();
            String oldDescription = "Old desc";
            String newDescription = "New desc";
            BigDecimal amount = BigDecimal.valueOf(20);
            ExpenseCategory oldCategory = ExpenseCategory.EATING_OUT;
            ExpenseCategory newCategory = ExpenseCategory.HOUSING;
            Instant expenseTime = Instant.now();
            Instant newExpenseTime = expenseTime.plus(Duration.ofMinutes(10));
            Expense existing = buildExpense(token, oldDescription, amount, expenseTime, oldCategory);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.of(existing));
            when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateExpenseRequest request = new UpdateExpenseRequest(
                    newDescription, null, newCategory, newExpenseTime);

            ExpenseResponse response = expenseService.updateExpense(token, request, EMAIL);

            assertThat(response.description()).isEqualTo(newDescription);
            assertThat(response.amount()).isEqualByComparingTo(amount);
            assertThat(response.category()).isEqualTo(newCategory);
            assertThat(response.expenseTime()).isEqualTo(newExpenseTime);
        }

        @Test
        void shouldThrowWhenExpenseNotFoundOnUpdate() {
            UUID token = UUID.randomUUID();
            String description = "New desc";
            BigDecimal amount = BigDecimal.valueOf(20);
            ExpenseCategory category = ExpenseCategory.EATING_OUT;
            Instant expenseTime = Instant.now();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.empty());

            UpdateExpenseRequest request = new UpdateExpenseRequest(description, amount, category, expenseTime);

            assertThatThrownBy(() -> expenseService.updateExpense(token, request, EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Expense not found");

            verify(expenseRepository, never()).save(any());
        }
    }

    // ---------- deleteExpense ----------

    @Nested
    class DeleteExpense {

        @Test
        void shouldDeleteExpenseWhenFound() {
            UUID token = UUID.randomUUID();
            String description = "To delete";
            BigDecimal amount = BigDecimal.ONE;
            ExpenseCategory category = ExpenseCategory.EATING_OUT;
            Instant expenseTime = Instant.now();
            Expense existing = buildExpense(token, description, amount, expenseTime, category);

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.of(existing));

            expenseService.deleteExpense(token, EMAIL);

            verify(expenseRepository).delete(existing);
        }

        @Test
        void shouldThrowWhenExpenseNotFoundOnDelete() {
            UUID token = UUID.randomUUID();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserIdAndToken(USERID, token)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.deleteExpense(token, EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Expense not found");

            verify(expenseRepository, never()).delete(any());
        }

        @Test
        void shouldThrowWhenUserNotFoundOnDelete() {
            UUID token = UUID.randomUUID();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> expenseService.deleteExpense(token, EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");

            verifyNoInteractions(expenseRepository);
        }
    }

    private Expense buildExpense(UUID token, String description, BigDecimal amount, Instant expenseTime, ExpenseCategory category) {
        Expense expense = new Expense();
        expense.setToken(token);
        expense.setDescription(description);
        expense.setAmount(amount);
        expense.setCategory(category);
        expense.setUser(user);
        expense.setExpenseTime(expenseTime);
        expense.setCreatedTime(LocalDateTime.now().toInstant(ZoneOffset.UTC));
        expense.setUpdatedTime(LocalDateTime.now().toInstant(ZoneOffset.UTC));
        return expense;
    }
}