package com.expensetracker.integrationTests;

import com.expensetracker.dto.*;
import com.expensetracker.util.ExpenseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
//TODO: fix fitler with categories
class ExpenseIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldSupportFullCreateReadUpdateDeleteFlow() {
        String token = registerAndGetToken(uniqueEmail(), "Password123!");
        HttpHeaders headers = authHeaders(token);
        String description = "Dinner";
        BigDecimal amount = BigDecimal.valueOf(45.50);
        ExpenseCategory category = ExpenseCategory.EATING_OUT;
        Instant expenseTime = Instant.now();
        // Create
        CreateExpenseRequest createRequest = new CreateExpenseRequest(
                description, amount, null, expenseTime, category);
        ResponseEntity<ExpenseResponse> createResponse = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST,
                new HttpEntity<>(createRequest, headers), ExpenseResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ExpenseResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.description()).isEqualTo(description);
        assertThat(created.amount()).isEqualByComparingTo(amount);
        assertThat(created.category()).isEqualTo(category);
        assertThat(created.expenseTime()).isEqualTo(expenseTime);
        assertThat(created.createdTime()).isNotNull();
        assertThat(created.updatedTime()).isNotNull();
        UUID expenseToken = created.expenseToken();
        Instant createdTime = created.createdTime();
        // Read by token
        ResponseEntity<ExpenseResponse> getResponse = restTemplate.exchange(
                "/api/expenses/{token}", HttpMethod.GET,
                new HttpEntity<>(headers), ExpenseResponse.class, expenseToken);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().description()).isEqualTo(description);
        assertThat(getResponse.getBody().category()).isEqualTo(category);
        assertThat(getResponse.getBody().expenseTime()).isEqualTo(expenseTime);
        assertThat(getResponse.getBody().amount()).isEqualByComparingTo(amount);

        // Read all
        ResponseEntity<PagedResponse<ExpenseResponse>> listResponse = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody().getData()).hasSize(1);

        // Update
        String updatedDescription = "Dinner with friends";
        BigDecimal updatedAmount = BigDecimal.valueOf(60);
        ExpenseCategory updatedExpenseCategory = ExpenseCategory.EATING_OUT;
        Instant updatedExpenseTime = expenseTime.plus(Duration.ofMinutes(10));
        UpdateExpenseRequest updateRequest = new UpdateExpenseRequest(
                updatedDescription, BigDecimal.valueOf(60), ExpenseCategory.EATING_OUT, updatedExpenseTime);
        ResponseEntity<ExpenseResponse> updateResponse = restTemplate.exchange(
                "/api/expenses/{token}", HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers), ExpenseResponse.class, expenseToken);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().description()).isEqualTo(updatedDescription);
        assertThat(updateResponse.getBody().category()).isEqualTo(updatedExpenseCategory);
        assertThat(updateResponse.getBody().amount()).isEqualByComparingTo(updatedAmount);
        assertThat(updateResponse.getBody().expenseTime()).isEqualTo(updatedExpenseTime);
        assertThat(updateResponse.getBody().updatedTime()).isNotEqualTo(createdTime);

        // Delete
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/expenses/{token}", HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class, expenseToken);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Confirm it's actually gone
        ResponseEntity<ErrorResponse> afterDelete = restTemplate.exchange(
                "/api/expenses/{token}", HttpMethod.GET,
                new HttpEntity<>(headers), ErrorResponse.class, expenseToken);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(afterDelete.getBody()).isNotNull();
        assertThat(afterDelete.getBody().message()).isEqualTo("Expense not found");
    }

    @Test
    void shouldFilterExpensesByCategory() {
        String token = registerAndGetToken(uniqueEmail(), "Password123!");
        HttpHeaders headers = authHeaders(token);
        String description1 = "Lunch";
        BigDecimal amount1 = BigDecimal.TEN;
        ExpenseCategory category1 = ExpenseCategory.EATING_OUT;
        Instant expenseTime1 = Instant.now();
        String description2 = "Rent";
        BigDecimal amount2 = BigDecimal.valueOf(1200);
        ExpenseCategory category2 = ExpenseCategory.HOUSING;
        Instant expenseTime2 = expenseTime1.plus(Duration.ofMinutes(10));
        restTemplate.exchange("/api/expenses", HttpMethod.POST,
                new HttpEntity<>(new CreateExpenseRequest(description1, amount1, null, expenseTime1, category1), headers),
                ExpenseResponse.class);
        restTemplate.exchange("/api/expenses", HttpMethod.POST,
                new HttpEntity<>(new CreateExpenseRequest(description2, amount2, null, expenseTime2, category2), headers),
                ExpenseResponse.class);

        ResponseEntity<PagedResponse<ExpenseResponse>> response = restTemplate.exchange(
                "/api/expenses?expenseCategory=EATING_OUT", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).description()).isEqualTo(description1);
        assertThat(response.getBody().getData().get(0).amount()).isEqualByComparingTo(amount1);
        assertThat(response.getBody().getData().get(0).category()).isEqualTo(category1);
        assertThat(response.getBody().getData().get(0).expenseTime()).isEqualTo(expenseTime1);
    }

    @Test
    void shouldReturn401WhenNoTokenProvided() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/expenses", ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WithGarbageToken() {
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(authHeaders("not-a-real-jwt")), ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userExpenseListShouldNotIncludeAnotherUsersExpenses() {
        String userAToken = registerAndGetToken(uniqueEmail(), "Password123!");
        String userBToken = registerAndGetToken(uniqueEmail(), "Password123!");
        String description = "User A's expense";
        BigDecimal amount = BigDecimal.TEN;
        ExpenseCategory category = ExpenseCategory.EATING_OUT;
        Instant expenseTime = Instant.now();
        restTemplate.exchange("/api/expenses", HttpMethod.POST,
                new HttpEntity<>(new CreateExpenseRequest(description, amount,  null, expenseTime, category),
                        authHeaders(userAToken)),
                ExpenseResponse.class);
        ResponseEntity<PagedResponse<ExpenseResponse>> userBList = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userBToken)), new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});

        assertThat(userBList.getBody().getData()).isEmpty();
    }

    /**
     * This test documents a real authorization gap: ExpenseService.getExpense()
     * looks up by token only — it never checks the expense belongs to the
     * authenticated user. Any authenticated user who obtains another user's
     * expense token (guessable? leaked in a URL? logged?) can read, and — per
     * the same pattern in updateExpense/deleteExpense — modify or delete it.
     * This test currently FAILS against the code as written. I'm including it
     * so it's on record; you'll want to add an ownership check in the service
     * layer (verify expense.getUser().equals(user) before returning/mutating)
     * rather than deleting this test to make the suite green.
     */
    @Test
    void userShouldNotBeAbleToReadAnotherUsersExpenseByToken() {
        String userAToken = registerAndGetToken(uniqueEmail(), "Password123!");
        String userBToken = registerAndGetToken(uniqueEmail(), "Password123!");
        String description = "User A's expense";
        BigDecimal amount = BigDecimal.TEN;
        ExpenseCategory category = ExpenseCategory.EATING_OUT;
        Instant expenseTime = Instant.now();
        ResponseEntity<ExpenseResponse> createResponse = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST,
                new HttpEntity<>(new CreateExpenseRequest(description, amount, null, expenseTime, category),
                        authHeaders(userAToken)),
                ExpenseResponse.class);
        assertThat(createResponse.getBody()).isNotNull();
        UUID expenseToken = createResponse.getBody().expenseToken();

        ResponseEntity<String> userBGetAttempt = restTemplate.exchange(
                "/api/expenses/{token}", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userBToken)), String.class, expenseToken);

        assertThat(userBGetAttempt.getStatusCode())
                .as("User B should not be able to fetch User A's expense by token")
                .isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldFilterExpensesByMultipleCategories() {
        HttpHeaders headers = authHeaders(registerAndGetToken(uniqueEmail(), "Password123!"));
        Instant t = Instant.now();
        createExpense(headers, "Lunch", BigDecimal.TEN, ExpenseCategory.EATING_OUT, t);
        createExpense(headers, "Rent", BigDecimal.valueOf(1200), ExpenseCategory.HOUSING, t.plusSeconds(60));
        createExpense(headers, "Movie", BigDecimal.valueOf(20), ExpenseCategory.FUN, t.plusSeconds(120));

        ResponseEntity<PagedResponse<ExpenseResponse>> response = restTemplate.exchange(
                "/api/expenses?expenseCategory=EATING_OUT&expenseCategory=HOUSING", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});

        assertThat(response.getBody().getData()).hasSize(2)
                .extracting(ExpenseResponse::category)
                .containsExactlyInAnyOrder(ExpenseCategory.EATING_OUT, ExpenseCategory.HOUSING);
    }

    @Test
    void shouldFilterExpensesByDateRange() {
        LocalDateTime startDate = LocalDateTime.of(2026, 8, 1, 12, 12);
        LocalDateTime endDate = LocalDateTime.of(2026, 8, 30, 12, 12);
        HttpHeaders headers = authHeaders(registerAndGetToken(uniqueEmail(), "Password123!"));
        createExpense(headers, "Old", BigDecimal.TEN, ExpenseCategory.EATING_OUT, Instant.parse("2026-01-01T00:00:00Z"));
        createExpense(headers, "InRange", BigDecimal.TEN, ExpenseCategory.EATING_OUT, Instant.parse("2026-08-15T00:00:00Z"));
        createExpense(headers, "TooLate", BigDecimal.TEN, ExpenseCategory.EATING_OUT, Instant.parse("2026-12-01T00:00:00Z"));

        ResponseEntity<PagedResponse<ExpenseResponse>> response = restTemplate.exchange(
                "/api/expenses?startDate="+startDate.toString()+"&endDate="+endDate.toString(), HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).description()).isEqualTo("InRange");
    }

    @Test
    void shouldSortExpensesByAmountAscending() {
        HttpHeaders headers = authHeaders(registerAndGetToken(uniqueEmail(), "Password123!"));
        Instant t = Instant.now();
        createExpense(headers, "Big", BigDecimal.valueOf(100), ExpenseCategory.EATING_OUT, t);
        createExpense(headers, "Small", BigDecimal.valueOf(5), ExpenseCategory.EATING_OUT, t.plusSeconds(60));
        createExpense(headers, "Medium", BigDecimal.valueOf(50), ExpenseCategory.EATING_OUT, t.plusSeconds(120));

        ResponseEntity<PagedResponse<ExpenseResponse>> response = restTemplate.exchange(
                "/api/expenses?sortBy=amount&sortOrder=asc", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});

        assertThat(response.getBody().getData())
                .extracting(ExpenseResponse::description)
                .containsExactly("Small", "Medium", "Big");
    }

    @Test
    void shouldPaginateResults() {
        HttpHeaders headers = authHeaders(registerAndGetToken(uniqueEmail(), "Password123!"));
        Instant t = Instant.now();
        for (int i = 0; i < 15; i++) {
            createExpense(headers, "Expense " + i, BigDecimal.TEN, ExpenseCategory.EATING_OUT, t.plusSeconds(i));
        }

        ResponseEntity<PagedResponse<ExpenseResponse>> page1 = restTemplate.exchange(
                "/api/expenses?page=0&pageSize=10", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});
        assertThat(page1.getBody().getData()).hasSize(10);
        assertThat(page1.getBody().getTotalItems()).isEqualTo(15);
        assertThat(page1.getBody().getTotalPages()).isEqualTo(2);

        ResponseEntity<PagedResponse<ExpenseResponse>> page2 = restTemplate.exchange(
                "/api/expenses?page=1&pageSize=10", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<PagedResponse<ExpenseResponse>>() {});
        assertThat(page2.getBody().getData()).hasSize(5);
    }

    @Test
    void shouldReturn400WhenSortFieldIsInvalid() {
        HttpHeaders headers = authHeaders(registerAndGetToken(uniqueEmail(), "Password123!"));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/expenses?sortBy=notAField", HttpMethod.GET,
                new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void createExpense(HttpHeaders headers, String description, BigDecimal amount,
                               ExpenseCategory category, Instant expenseTime) {
        restTemplate.exchange("/api/expenses", HttpMethod.POST,
                new HttpEntity<>(new CreateExpenseRequest(description, amount, null, expenseTime, category), headers),
                ExpenseResponse.class);
    }
}