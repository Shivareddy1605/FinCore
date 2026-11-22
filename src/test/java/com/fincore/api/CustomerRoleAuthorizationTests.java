
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
class CustomerRoleAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    private UserAccount createUser(
            String email,
            String role,
            UUID customerId) {

        UserAccount user = new UserAccount(UUID.randomUUID());

        user.setEmail(email);
        user.setPasswordHash("test-only-unused-password-hash");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCustomerId(customerId);

        return userAccountRepository.saveAndFlush(user);
    }

    private Customer createCustomer(String email) {

        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName("Test Customer");
        customer.setEmail(email);
        customer.setPhoneNumber("9876543210");

        return customerRepository.saveAndFlush(customer);
    }

    @Test
    void operationsUserCannotAccessBorrowerCustomer()
            throws Exception {

        String email =
                "operations-" + UUID.randomUUID() + "@example.com";

        Customer customer = createCustomer(email);

        UserAccount user = createUser(
                email,
                "OPERATIONS",
                customer.getId()
        );

        String token = jwtService.generateToken(user);

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
    void adminUserCannotAccessBorrowerCustomer()
            throws Exception {

        String email =
                "admin-" + UUID.randomUUID() + "@example.com";

        Customer customer = createCustomer(email);

        UserAccount user = createUser(
                email,
                "ADMIN",
                customer.getId()
        );

        String token = jwtService.generateToken(user);

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
    void operationsUserCannotCreateBorrowerCustomer()
            throws Exception {

        String email =
                "operations-create-" + UUID.randomUUID()
                        + "@example.com";

        UserAccount user = createUser(email, "OPERATIONS", null);

        String token = jwtService.generateToken(user);

        String requestBody = """
                {
                    "fullName": "Operations User",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

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

        assertFalse(customerRepository.existsByEmail(email));
    }

    @Test
    void adminUserCannotCreateBorrowerCustomer()
            throws Exception {

        String email =
                "admin-create-" + UUID.randomUUID()
                        + "@example.com";

        UserAccount user = createUser(email, "ADMIN", null);

        String token = jwtService.generateToken(user);

        String requestBody = """
                {
                    "fullName": "Admin User",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

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

        assertFalse(customerRepository.existsByEmail(email));
    }

    @Test
    void operationsUserCannotUpdateBorrowerCustomer()
            throws Exception {

        String email =
                "operations-update-" + UUID.randomUUID()
                        + "@example.com";

        Customer customer = createCustomer(email);

        UserAccount user = createUser(
                email,
                "OPERATIONS",
                customer.getId()
        );

        String token = jwtService.generateToken(user);

        String updateBody = """
                {
                    "fullName": "Changed Customer",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        mockMvc.perform(
                put("/api/customers/{id}", customer.getId())
                        .with(csrf())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
        )
                .andExpect(status().isForbidden());

        Customer unchanged = customerRepository
                .findById(customer.getId())
                .orElseThrow();

        assertEquals("Test Customer", unchanged.getFullName());
        assertEquals("9876543210", unchanged.getPhoneNumber());
    }

    @Test
    void adminUserCannotUpdateBorrowerCustomer()
            throws Exception {

        String email =
                "admin-update-" + UUID.randomUUID()
                        + "@example.com";

        Customer customer = createCustomer(email);

        UserAccount user = createUser(
                email,
                "ADMIN",
                customer.getId()
        );

        String token = jwtService.generateToken(user);

        String updateBody = """
                {
                    "fullName": "Changed Customer",
                    "email": "%s",
                    "phoneNumber": "9123456789"
                }
                """.formatted(email);

        mockMvc.perform(
                put("/api/customers/{id}", customer.getId())
                        .with(csrf())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
        )
                .andExpect(status().isForbidden());

        Customer unchanged = customerRepository
                .findById(customer.getId())
                .orElseThrow();

        assertEquals("Test Customer", unchanged.getFullName());
        assertEquals("9876543210", unchanged.getPhoneNumber());
    }
}
