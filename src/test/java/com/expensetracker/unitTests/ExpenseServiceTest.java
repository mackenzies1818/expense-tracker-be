package com.expensetracker.unitTests;

import com.expensetracker.dto.CreateExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.UpdateExpenseRequest;
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
    class GetExpensesForUser {

        @Test
        void shouldReturnAllExpensesWhenCategoryIsNull() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            String e1Description = "B";
            BigDecimal e1Amount = BigDecimal.ONE;
            ExpenseCategory e1Category = ExpenseCategory.EATING_OUT;
            String e2Description = "A";
            BigDecimal e2Amount = BigDecimal.TEN;
            ExpenseCategory e2Category = ExpenseCategory.HOUSING;
            Instant expenseTime = Instant.now();
            Expense e1 = buildExpense(UUID.randomUUID(), e1Description, e1Amount, expenseTime, e1Category);
            Expense e2 = buildExpense(UUID.randomUUID(), e2Description, e2Amount, expenseTime, e2Category);

            when(expenseRepository.findByUserId(user.getId())).thenReturn(List.of(e1, e2));

            List<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, null);

            assertThat(result).hasSize(2);
            verify(expenseRepository, never()).findByUserIdAndCategory(any(), any());
        }

        @Test
        void shouldReturnFilteredExpensesWhenCategoryProvided() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            String e1Description = "B";
            BigDecimal e1Amount = BigDecimal.ONE;
            ExpenseCategory e1Category = ExpenseCategory.EATING_OUT;
            Instant expenseTime = Instant.now();
            Expense e1 = buildExpense(UUID.randomUUID(), e1Description, e1Amount, expenseTime, e1Category);

            when(expenseRepository.findByUserIdAndCategory(user.getId(), e1Category))
                    .thenReturn(List.of(e1));

            List<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, e1Category);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).category()).isEqualTo(e1Category);
            assertThat(result.get(0).amount()).isEqualTo(e1Amount);
            assertThat(result.get(0).description()).isEqualTo(e1Description);
            assertThat(result.get(0).expenseTime()).isEqualTo(expenseTime);
            verify(expenseRepository, never()).findByUserId(any());
        }

        @Test
        void shouldReturnEmptyListWhenNoExpenses() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(expenseRepository.findByUserId(user.getId())).thenReturn(List.of());

            List<ExpenseResponse> result = expenseService.getExpensesForUser(EMAIL, null);

            assertThat(result).isEmpty();
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