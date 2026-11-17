
package com.fincore.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FincoreApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

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

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/customers")
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerRetrievalReturnsExistingCustomer() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customerId = new tools.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();

        mockMvc.perform(get("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value("9876543210"));
    }

    @Test
    void customerRetrievalReturns404ForUnknownId() throws Exception {

        String unknownId = "550e8400-e29b-41d4-a716-446655440000";

        mockMvc.perform(get("/api/customers/" + unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerUpdateChangesProfileDetails() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";
        String updatedEmail = "rahulkumar-" + UUID.randomUUID() + "@example.com";

        String createBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customerId = new tools.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(updatedEmail);

        mockMvc.perform(put("/api/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.phoneNumber").value("9123456789"));

        mockMvc.perform(get("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.email").value(updatedEmail))
                .andExpect(jsonPath("$.phoneNumber").value("9123456789"));
    }

    @Test
    void customerUpdatePreservesIdAndCreatedAt() throws Exception {

        String email = "rahul-" + UUID.randomUUID() + "@example.com";
        String updatedEmail = "rahulkumar-" + UUID.randomUUID() + "@example.com";

        String createBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        mockMvc.perform(get("/api/customers/" + customerId))
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerUpdateReturns404ForUnknownId() throws Exception {

        String unknownId = "550e8400-e29b-41d4-a716-446655440000";

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "rahulkumar@example.com",
                    "phoneNumber": "9123456789"
                }
                """;

        mockMvc.perform(put("/api/customers/" + unknownId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerRegistrationRejectsDuplicateEmail() throws Exception {

        String email = "duplicate-" + UUID.randomUUID() + "@example.com";

        String firstCustomer = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String secondCustomer = """
                {
                    "fullName": "Amit Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstCustomer))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondCustomer))
                .andExpect(status().isConflict());
    }

    @Test
    void customerUpdateRejectsDuplicateEmail() throws Exception {

        String firstEmail = "first-" + UUID.randomUUID() + "@example.com";
        String secondEmail = "second-" + UUID.randomUUID() + "@example.com";

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
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstCustomer))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondCustomer))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondCustomerId = new tools.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();

        String updateBody = """
                {
                    "fullName": "Amit Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(firstEmail);

        mockMvc.perform(put("/api/customers/" + secondCustomerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/customers/" + secondCustomerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(secondEmail));
    }

    @Test
    void customerUpdateAllowsExistingEmail() throws Exception {

        String email = "existing-" + UUID.randomUUID() + "@example.com";

        String createBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customerId = new tools.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();

        String updateBody = """
                {
                    "fullName": "Rahul Kumar",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        mockMvc.perform(put("/api/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Rahul Kumar"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phoneNumber").value("9123456789"));
    }
}
