
package com.fincore.api;

import com.fincore.api.entity.Customer;
import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.CustomerRepository;
import com.fincore.api.repository.UserAccountRepository;
import com.fincore.api.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void borrowerCannotAccessUnlinkedCustomer() throws Exception {

        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName("Test Customer");
        customer.setEmail(
                "customer-" + UUID.randomUUID() + "@example.com");
        customer.setPhoneNumber("9876543210");

        customerRepository.saveAndFlush(customer);

        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(
                "borrower-" + UUID.randomUUID() + "@example.com");
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        assertNull(borrower.getCustomerId());

        String token = jwtService.generateToken(borrower);

        mockMvc.perform(
                get("/api/customers/{id}", customer.getId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        )
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingCustomerProfileLinksAuthenticatedBorrower()
            throws Exception {

        String email =
                "profile-" + UUID.randomUUID() + "@example.com";

        // Create an active borrower without a customer profile.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        assertNull(borrower.getCustomerId());

        // Generate a real JWT for this borrower.
        String token = jwtService.generateToken(borrower);

        String requestBody = """
                {
                    "fullName": "Test Borrower",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        // Create a customer profile using the borrower's JWT.
        String response = mockMvc.perform(
                post("/api/customers")
                        .with(csrf())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract the newly created customer ID.
        String customerId = new tools.jackson.databind.ObjectMapper()
                .readTree(response)
                .get("id")
                .asText();

        // Verify the customer record exists.
        assertTrue(
                customerRepository.existsById(
                        UUID.fromString(customerId))
        );

        // Verify ownership is stored in the user account.
        UserAccount updatedBorrower =
                userAccountRepository.findById(borrower.getId())
                        .orElseThrow();

        assertNotNull(updatedBorrower.getCustomerId());

        assertEquals(
                UUID.fromString(customerId),
                updatedBorrower.getCustomerId()
        );
    }

    @Test
    void borrowerCannotCreateSecondCustomerProfile()
            throws Exception {

        String email =
                "duplicate-profile-" + UUID.randomUUID()
                        + "@example.com";

        // Create an active borrower without a customer profile.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        String token = jwtService.generateToken(borrower);

        String requestBody = """
                {
                    "fullName": "Test Borrower",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        // First registration must succeed.
        String response = mockMvc.perform(
                post("/api/customers")
                        .with(csrf())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID originalCustomerId = UUID.fromString(
                new tools.jackson.databind.ObjectMapper()
                        .readTree(response)
                        .get("id")
                        .asText()
        );

        assertTrue(customerRepository.existsById(originalCustomerId));

        // The same borrower must not create another profile.
        mockMvc.perform(
                post("/api/customers")
                        .with(csrf())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        )
                .andExpect(status().isConflict());

        // The original ownership relationship must remain intact.
        UserAccount updatedBorrower =
                userAccountRepository.findById(borrower.getId())
                        .orElseThrow();

        assertEquals(
                originalCustomerId,
                updatedBorrower.getCustomerId()
        );

        assertTrue(customerRepository.existsById(originalCustomerId));
    }
}
