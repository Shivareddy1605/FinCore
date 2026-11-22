
package com.fincore.api;

import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.CustomerRepository;
import com.fincore.api.repository.UserAccountRepository;
import com.fincore.api.service.JwtService;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerTransactionRollbackTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @MockitoSpyBean
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void customerCreationRollsBackWhenAccountLinkingFails()
            throws Exception {

        String email =
                "rollback-" + UUID.randomUUID() + "@example.com";

        // Create a borrower without a linked customer profile.
        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        String token = jwtService.generateToken(borrower);

        // Simulate a failure when saving the account link.
        doThrow(new IllegalStateException(
                "Simulated account-linking failure"))
                .when(userAccountRepository)
                .saveAndFlush(argThat(
                        account -> account != null
                                && account.getCustomerId() != null
                ));

        String requestBody = """
                {
                    "fullName": "Rollback Test Customer",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        // Registration should fail after customer insertion.
        assertThrows(
                ServletException.class,
                () -> mockMvc.perform(
                        post("/api/customers")
                                .with(csrf())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
        );

        // The customer insert must have been rolled back.
        assertFalse(
                customerRepository.existsByEmail(email),
                "Failed registration must not leave a customer record"
        );

        // The borrower must remain without a customer link.
        UserAccount savedBorrower =
                userAccountRepository.findById(borrower.getId())
                        .orElseThrow();

        assertNull(
                savedBorrower.getCustomerId(),
                "Failed registration must not link the borrower"
        );
    }
}
