package com.expensetracker.integrationTests;

import com.expensetracker.dto.CreateExpenseRequest;
import com.expensetracker.dto.ErrorResponse;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.UpdateExpenseRequest;
import com.expensetracker.util.ExpenseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        ResponseEntity<ExpenseResponse[]> listResponse = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(headers), ExpenseResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);

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

        ResponseEntity<ExpenseResponse[]> response = restTemplate.exchange(
                "/api/expenses?expenseCategory=EATING_OUT", HttpMethod.GET,
                new HttpEntity<>(headers), ExpenseResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].description()).isEqualTo(description1);
        assertThat(response.getBody()[0].amount()).isEqualByComparingTo(amount1);
        assertThat(response.getBody()[0].category()).isEqualTo(category1);
        assertThat(response.getBody()[0].expenseTime()).isEqualTo(expenseTime1);
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

        ResponseEntity<ExpenseResponse[]> userBList = restTemplate.exchange(
                "/api/expenses", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userBToken)), ExpenseResponse[].class);

        assertThat(userBList.getBody()).isEmpty();
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
}