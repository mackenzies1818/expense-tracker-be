package com.expensetracker.functionalTests;

import com.expensetracker.auth.SecurityConfig;
import com.expensetracker.controller.ExpenseController;
import com.expensetracker.dto.*;
import com.expensetracker.exceptions.InvalidFilterException;
import com.expensetracker.services.ExpenseService;
import com.expensetracker.util.ExpenseCategory;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.json.JsonMapper; // was com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest; // new package
import org.springframework.test.context.bean.override.mockito.MockitoBean; // was @MockBean
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Nested;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;


@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class) // your actual security config class
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ExpenseService expenseService;

    private static final String EMAIL = "user@example.com";

    // Required because SecurityConfig's securityFilterChain wires oauth2ResourceServer().jwt(),
    // which needs a JwtDecoder bean to exist in the context — even though .with(jwt()) below
    // bypasses actually calling it (it injects the Authentication directly).
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ---------- POST /api/expenses ----------

    @Test
    void createExpense_shouldReturn201AndBody() throws Exception {
        UUID token = UUID.randomUUID();
        String description = "Weekly groceries";
        BigDecimal amount = BigDecimal.valueOf(50);
        ExpenseCategory category = ExpenseCategory.GROCERIES;
        Instant expenseTime = Instant.now();
        CreateExpenseRequest request = new CreateExpenseRequest(
                description, amount, null, expenseTime, category);
        ExpenseResponse response = new ExpenseResponse(
                token, description, amount, category, expenseTime,
                Instant.now(), Instant.now());

        when(expenseService.createExpense(any(CreateExpenseRequest.class), eq(EMAIL)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .with(jwt().jwt(j -> j.subject(EMAIL)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.category").value(category.toString()))
                .andExpect(jsonPath("$.amount").value(amount))
                .andExpect(jsonPath("$.expenseTime").value(expenseTime.toString()))
                .andExpect(jsonPath("$.expenseToken").value(token.toString()));
    }

    @Test
    void createExpense_shouldReturn401WhenUnauthenticated() throws Exception {
        String description = "Weekly groceries";
        BigDecimal amount = BigDecimal.valueOf(50);
        ExpenseCategory category = ExpenseCategory.GROCERIES;
        Instant expenseTime = Instant.now();
        CreateExpenseRequest request = new CreateExpenseRequest(
                description, amount, null, expenseTime, category);

        mockMvc.perform(post("/api/expenses")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/expenses/{token} ----------

    @Test
    void getExpense_shouldReturn200AndBody() throws Exception {
        UUID token = UUID.randomUUID();
        String description = "Coffee";
        BigDecimal amount = BigDecimal.valueOf(4.5);
        ExpenseCategory category = ExpenseCategory.EATING_OUT;
        Instant expenseTime = Instant.now();
        ExpenseResponse response = new ExpenseResponse(
                token, description, amount, category, expenseTime,
                Instant.now(), Instant.now());

        when(expenseService.getExpense(eq(token), eq(EMAIL))).thenReturn(response);

        mockMvc.perform(get("/api/expenses/{token}", token)
                        .with(jwt().jwt(j -> j.subject(EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.category").value(category.toString()))
                .andExpect(jsonPath("$.expenseTime").value(expenseTime.toString()))
                .andExpect(jsonPath("$.amount").value(amount));
    }

    @Test
    void getExpense_shouldReturn400WhenTokenIsNotValidUuid() throws Exception {
        mockMvc.perform(get("/api/expenses/{token}", "not-a-uuid")
                        .with(jwt().jwt(j -> j.subject(EMAIL))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getExpense_shouldReturn500WhenServiceThrows() throws Exception {
        UUID token = UUID.randomUUID();
        when(expenseService.getExpense(eq(token), eq(EMAIL)))
                .thenThrow(new RuntimeException("Expense not found"));

        mockMvc.perform(get("/api/expenses/{token}", token)
                        .with(jwt().jwt(j -> j.subject(EMAIL))))
                .andExpect(status().is5xxServerError());
    }

    // ---------- GET /api/expenses ----------

    @Nested
    class GetExpenses {

        @Test
        void shouldReturnPagedExpensesWithNoFilters() throws Exception {
            UUID token = UUID.randomUUID();
            String description = "Updated";
            BigDecimal amount = BigDecimal.valueOf(4.5);
            ExpenseCategory category = ExpenseCategory.HOUSING;
            Instant expenseTime = Instant.now();
            ExpenseResponse response = new ExpenseResponse(
                    token, description, amount, category, expenseTime,
                    Instant.now(), Instant.now());
            PagedResponse<ExpenseResponse> paged = new PagedResponse<>(List.of(response), 0, 10, 1, 1);

            when(expenseService.getExpensesForUser(eq(EMAIL), any(ExpenseFilter.class))).thenReturn(paged);

            mockMvc.perform(get("/api/expenses")
                            .with(jwt().jwt(j -> j.subject(EMAIL))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.totalItems").value(1));
        }

        @Test
        void shouldPassDateRangeFilterThrough() throws Exception {
            ArgumentCaptor<ExpenseFilter> captor = ArgumentCaptor.forClass(ExpenseFilter.class);
            when(expenseService.getExpensesForUser(eq(EMAIL), any(ExpenseFilter.class)))
                    .thenReturn(new PagedResponse<>(List.of(), 0, 10, 0, 0));
            LocalDateTime startDate = LocalDateTime.of(2026, 8, 1, 12, 12);
            LocalDateTime endDate = LocalDateTime.of(2026, 8, 31, 12, 12);
            mockMvc.perform(get("/api/expenses")
                            .param("startDate", startDate.toString())
                            .param("endDate", endDate.toString())
                            .with(jwt().jwt(j -> j.subject(EMAIL))))
                    .andExpect(status().isOk());

            verify(expenseService).getExpensesForUser(eq(EMAIL), captor.capture());
            assertThat(captor.getValue().getStartDate()).isEqualTo(startDate);
            assertThat(captor.getValue().getEndDate()).isEqualTo(endDate);
        }

        @Test
        void shouldPassMultipleCategoriesThrough() throws Exception {
            ArgumentCaptor<ExpenseFilter> captor = ArgumentCaptor.forClass(ExpenseFilter.class);
            when(expenseService.getExpensesForUser(eq(EMAIL), any(ExpenseFilter.class)))
                    .thenReturn(new PagedResponse<>(List.of(), 0, 10, 0, 0));

            mockMvc.perform(get("/api/expenses")
                            .param("expenseCategory", ExpenseCategory.EATING_OUT.name(), ExpenseCategory.HOUSING.name())
                            .with(jwt().jwt(j -> j.subject(EMAIL))))
                    .andExpect(status().isOk());
            verify(expenseService).getExpensesForUser(eq(EMAIL), captor.capture());
            assertThat(captor.getValue().getCategories())
                    .containsExactlyInAnyOrder(ExpenseCategory.EATING_OUT, ExpenseCategory.HOUSING);
        }

        @Test
        void shouldPassSortAndPaginationThrough() throws Exception {
            ArgumentCaptor<ExpenseFilter> captor = ArgumentCaptor.forClass(ExpenseFilter.class);
            when(expenseService.getExpensesForUser(eq(EMAIL), any(ExpenseFilter.class)))
                    .thenReturn(new PagedResponse<>(List.of(), 2, 5, 0, 0));

            mockMvc.perform(get("/api/expenses")
                            .param("sortBy", "amount")
                            .param("sortOrder", "asc")
                            .param("page", "2")
                            .param("pageSize", "5")
                            .with(jwt().jwt(j -> j.subject(EMAIL))))
                    .andExpect(status().isOk());

            verify(expenseService).getExpensesForUser(eq(EMAIL), captor.capture());
            ExpenseFilter captured = captor.getValue();
            assertThat(captured.getSortBy()).isEqualTo("amount");
            assertThat(captured.getSortOrder()).isEqualTo(Sort.Direction.ASC);
            assertThat(captured.getPage()).isEqualTo(2);
            assertThat(captured.getPageSize()).isEqualTo(5);
        }

        @Test
        void shouldReturn400WhenServiceThrowsInvalidFilterException() throws Exception {
            String errorMessage ="Invalid sortBy field";
            when(expenseService.getExpensesForUser(eq(EMAIL), any(ExpenseFilter.class)))
                    .thenThrow(new InvalidFilterException(errorMessage));

            mockMvc.perform(get("/api/expenses")
                            .param("sortBy", "whatever") // value doesn't matter — service is mocked
                            .with(jwt().jwt(j -> j.subject(EMAIL))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(errorMessage));
        }

        @Test
        void shouldReturn400WhenStartDateAfterEndDate() throws Exception {
            mockMvc.perform(get("/api/expenses")
                            .param("startDate", "2026-09-01")
                            .param("endDate", "2026-08-01")
                            .with(jwt().jwt(j -> j.subject(EMAIL))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid value for parameter 'startDate'"));
        }
    }

    // ---------- PUT /api/expenses/{token} ----------

    @Test
    void updateExpense_shouldReturn200AndUpdatedBody() throws Exception {
        UUID token = UUID.randomUUID();
        String description = "Updated";
        BigDecimal amount = BigDecimal.valueOf(4.5);
        ExpenseCategory category = ExpenseCategory.HOUSING;
        Instant expenseTime = Instant.now();
        UpdateExpenseRequest request = new UpdateExpenseRequest(
                description, amount, category, expenseTime);
        ExpenseResponse response = new ExpenseResponse(
                token, description, amount, category, expenseTime,
                Instant.now(), Instant.now());

        when(expenseService.updateExpense(eq(token), any(UpdateExpenseRequest.class), eq(EMAIL)))
                .thenReturn(response);

        mockMvc.perform(put("/api/expenses/{token}", token)
                        .with(jwt().jwt(j -> j.subject(EMAIL)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(description));
    }

    // ---------- DELETE /api/expenses/{token} ----------

    @Test
    void deleteExpense_shouldReturn204() throws Exception {
        UUID token = UUID.randomUUID();

        mockMvc.perform(delete("/api/expenses/{token}", token).with(jwt().jwt(j -> j.subject(EMAIL))))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteExpense_shouldReturn401WhenUnauthenticated() throws Exception {
        UUID token = UUID.randomUUID();

        mockMvc.perform(delete("/api/expenses/{token}", token))
                .andExpect(status().isUnauthorized());
    }
}
