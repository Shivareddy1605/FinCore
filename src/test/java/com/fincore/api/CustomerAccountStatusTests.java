
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerAccountStatusTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void disabledBorrowerCannotAccessCustomerWithExistingJwt()
            throws Exception {

        String email =
                "disabled-" + UUID.randomUUID() + "@example.com";

        // Create a customer profile.
        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName("Test Borrower");
        customer.setEmail(email);
        customer.setPhoneNumber("9876543210");

        customer = customerRepository.saveAndFlush(customer);

        // Create an active borrower linked to this customer.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");
        borrower.setCustomerId(customer.getId());

        borrower = userAccountRepository.saveAndFlush(borrower);

        // Generate JWT while the account is active.
        String token = jwtService.generateToken(borrower);

        // Disable the account after JWT issuance.
        borrower.setStatus("DISABLED");
        userAccountRepository.saveAndFlush(borrower);

        assertEquals("DISABLED", borrower.getStatus());

        // The existing JWT must not bypass account restrictions.
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
    void disabledBorrowerCannotUpdateCustomerWithExistingJwt()
            throws Exception {

        String email =
                "disabled-update-" + UUID.randomUUID()
                        + "@example.com";

        // Create a customer profile.
        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName("Original Borrower");
        customer.setEmail(email);
        customer.setPhoneNumber("9876543210");

        customer = customerRepository.saveAndFlush(customer);

        UUID customerId = customer.getId();

        // Create an active borrower linked to the customer.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");
        borrower.setCustomerId(customerId);

        borrower = userAccountRepository.saveAndFlush(borrower);

        // Generate a valid JWT while the account is active.
        String token = jwtService.generateToken(borrower);

        // Disable the account after issuing the JWT.
        borrower.setStatus("DISABLED");
        userAccountRepository.saveAndFlush(borrower);

        assertEquals("DISABLED", borrower.getStatus());

        String updateBody = """
                {
                    "fullName": "Updated Borrower",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        // A disabled borrower must not update the customer.
        mockMvc.perform(
                put("/api/customers/{id}", customerId)
                        .with(csrf())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
        )
                .andExpect(status().isForbidden());

        // Verify that the customer details remain unchanged.
        Customer unchangedCustomer =
                customerRepository.findById(customerId)
                        .orElseThrow();

        assertEquals(
                "Original Borrower",
                unchangedCustomer.getFullName()
        );

        assertEquals(
                email,
                unchangedCustomer.getEmail()
        );

        assertEquals(
                "9876543210",
                unchangedCustomer.getPhoneNumber()
        );
    }

    @Test
    void disabledBorrowerCannotCreateCustomerWithExistingJwt()
            throws Exception {

        String email =
                "disabled-create-" + UUID.randomUUID()
                        + "@example.com";

        // Create an active borrower with no customer profile.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        borrower = userAccountRepository.saveAndFlush(borrower);

        UUID borrowerId = borrower.getId();

        assertNull(borrower.getCustomerId());
        assertFalse(customerRepository.existsByEmail(email));

        // Issue JWT while the account is active.
        String token = jwtService.generateToken(borrower);

        // Disable the borrower after JWT issuance.
        borrower.setStatus("DISABLED");
        userAccountRepository.saveAndFlush(borrower);

        assertEquals("DISABLED", borrower.getStatus());

        String requestBody = """
                {
                    "fullName": "Disabled Test Borrower",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        // Registration must be rejected.
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
                .andExpect(status().isForbidden());

        // No customer profile should have been created.
        assertFalse(
                customerRepository.existsByEmail(email),
                "Disabled borrower must not create a customer profile"
        );

        // The account must remain disabled and unlinked.
        UserAccount savedBorrower =
                userAccountRepository.findById(borrowerId)
                        .orElseThrow();

        assertEquals("DISABLED", savedBorrower.getStatus());

        assertNull(
                savedBorrower.getCustomerId(),
                "Disabled borrower must remain unlinked"
        );
    }
}
