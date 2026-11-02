
package com.fincore.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "rahul@example.com",
                    "phoneNumber": "9876543210"
                }
                """;

        mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.email").value("rahul@example.com"))
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

        String requestBody = """
                {
                    "fullName": "Rahul Sharma",
                    "email": "rahul@example.com",
                    "phoneNumber": "9876543210"
                }
                """;

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
                .andExpect(jsonPath("$.email").value("rahul@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("9876543210"));
    }

    @Test
    void customerRetrievalReturns404ForUnknownId() throws Exception {

        String unknownId = "550e8400-e29b-41d4-a716-446655440000";

        mockMvc.perform(get("/api/customers/" + unknownId))
                .andExpect(status().isNotFound());
    }
}
