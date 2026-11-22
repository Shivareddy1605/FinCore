
package com.fincore.api;

import com.fincore.api.entity.Customer;
import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.CustomerRepository;
import com.fincore.api.repository.UserAccountRepository;
import com.fincore.api.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class FincoreApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JwtService jwtService;

    private BorrowerTestFixture borrowerFixture() {
        return new BorrowerTestFixture(
                userAccountRepository,
                jwtService);
    }

    // Create an unlinked test borrower whose account email
    // matches the email used for customer registration.
    private String createBorrowerToken(String email) {

        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        return jwtService.generateToken(borrower);
    }

    private String customerIdFromResponse(String response)
            throws Exception {

        return new tools.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("FinCore API"));
    }

    @Test
    void customerRegistrationReturnsIdAndTimestamp() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";
        String token = createBorrowerToken(email);

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value("9876543210"));
    }

    @Test
    void customerRegistrationRejectsInvalidEmail() throws Exception {

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "invalid-email",
                    "phoneNumber": "9876543210"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerRegistrationRejectsEmptyName() throws Exception {

        String requestBody = """
                {
                    "fullName": "",
                    "email": "rahul@example.com",
                    "phoneNumber": "9876543210"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerRegistrationRejectsInvalidPhoneNumber() throws Exception {

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "rahul@example.com",
                    "phoneNumber": "12345"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerRetrievalReturnsExistingCustomer() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";
        String token = createBorrowerToken(email);

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customerId = customerIdFromResponse(response);

        mockMvc.perform(get("/api/customers/" + customerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value("9876543210"));
    }

    @Test
    void customerRetrievalRejectsUnownedUnknownId() throws Exception {

        UUID unknownId = UUID.randomUUID();

        String token = borrowerFixture()
                .createUnlinkedBorrowerToken();

        mockMvc.perform(get("/api/customers/{id}", unknownId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerUpdateChangesProfileDetails() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";
        String updatedEmail = "rahulkumar-" + UUID.randomUUID()
                + "@example.com";

        String token = createBorrowerToken(email);

        String createBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customerId = customerIdFromResponse(response);

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(updatedEmail);

        mockMvc.perform(put("/api/customers/" + customerId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.phoneNumber").value("9123456789"));

        mockMvc.perform(get("/api/customers/" + customerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.phoneNumber").value("9123456789"));
    }

    @Test
    void customerUpdatePreservesIdAndCreatedAt() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";
        String updatedEmail = "rahulkumar-" + UUID.randomUUID()
                + "@example.com";

        String token = createBorrowerToken(email);

        String createBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var originalCustomer = new tools.jackson.databind.ObjectMapper()
                .readTree(response);

        String customerId = originalCustomer.get("id").asText();
        String createdAt = originalCustomer.get("createdAt").asText();

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(updatedEmail);

        mockMvc.perform(put("/api/customers/" + customerId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        mockMvc.perform(get("/api/customers/" + customerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void customerUpdateRejectsInvalidEmail() throws Exception {

        String invalidBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "invalid-email",
                    "phoneNumber": "9123456789"
                }
                """;

        String customerId = "550e8400-e29b-41d4-a716-446655440000";

        mockMvc.perform(put("/api/customers/" + customerId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerUpdateRejectsUnownedUnknownId() throws Exception {

        UUID unknownId = UUID.randomUUID();

        String token = borrowerFixture()
                .createUnlinkedBorrowerToken();

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "rahulkumar@example.com",
                    "phoneNumber": "9123456789"
                }
                """;

        mockMvc.perform(put("/api/customers/" + unknownId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerRegistrationRejectsDuplicateEmail() throws Exception {

        String email = "duplicate-" + UUID.randomUUID()
                + "@example.com";

        // Seed an existing customer to test duplicate-email handling.
        Customer existing = new Customer(UUID.randomUUID());

        existing.setFullName("Rahul Sharma");
        existing.setEmail(email);
        existing.setPhoneNumber("9876543210");

        customerRepository.saveAndFlush(existing);

        // A separate borrower has the same account email.
        String token = createBorrowerToken(email);

        String requestBody = """
                {
                    "fullName": "Amit Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void customerUpdateRejectsDuplicateEmail() throws Exception {

        String firstEmail = "first-" + UUID.randomUUID()
                + "@example.com";
        String secondEmail = "second-" + UUID.randomUUID()
                + "@example.com";

        String firstToken = createBorrowerToken(firstEmail);
        String secondToken = createBorrowerToken(secondEmail);

        String firstCustomer = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(firstEmail);

        String secondCustomer = """
                {
                    "fullName": "Amit Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(secondEmail);

        mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstCustomer))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondCustomer))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondCustomerId = customerIdFromResponse(response);

        String updateBody = """
                {
                    "fullName": "Amit Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(firstEmail);

        mockMvc.perform(put("/api/customers/" + secondCustomerId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/customers/" + secondCustomerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(secondEmail));
    }

    @Test
    void customerUpdateAllowsExistingEmail() throws Exception {

        String email = "existing-" + UUID.randomUUID()
                + "@example.com";

        String token = createBorrowerToken(email);

        String createBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customerId = customerIdFromResponse(response);

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        mockMvc.perform(put("/api/customers/" + customerId)
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value("9123456789"));
    }
}
