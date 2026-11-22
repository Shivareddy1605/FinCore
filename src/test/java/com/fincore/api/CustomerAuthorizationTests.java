
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        // Create a customer record.
        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName("Test Customer");
        customer.setEmail(
                "customer-" + UUID.randomUUID() + "@example.com");
        customer.setPhoneNumber("9876543210");

        customerRepository.saveAndFlush(customer);

        // Create a borrower account that is NOT linked
        // to the customer record.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(
                "borrower-" + UUID.randomUUID() + "@example.com");
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        // Confirm that the borrower has no linked customer.
        org.junit.jupiter.api.Assertions.assertNull(
                borrower.getCustomerId()
        );

        // Generate a valid JWT for this borrower.
        String token = jwtService.generateToken(borrower);

        // The borrower must NOT access an unlinked
        // customer record.
        mockMvc.perform(
                get("/api/customers/{id}", customer.getId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
        )
                .andExpect(status().isForbidden());
    }
}
