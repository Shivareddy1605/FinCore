
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerConcurrencyTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void concurrentRegistrationsCreateOnlyOneCustomer()
            throws Exception {

        String email =
                "concurrent-" + UUID.randomUUID() + "@example.com";

        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(email);
        borrower.setPasswordHash("test-only-unused-password-hash");
        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");

        userAccountRepository.saveAndFlush(borrower);

        String token = jwtService.generateToken(borrower);

        String requestBody = """
                {
                    "fullName": "Concurrent Test Borrower",
                    "email": "%s",
                    "phoneNumber": "9876543210"
                }
                """.formatted(email);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> registrationRequest = () -> {

            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent test start timed out");
            }

            return mockMvc.perform(
                    post("/api/customers")
                            .with(csrf())
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
            ).andReturn().getResponse().getStatus();
        };

        try {
            Future<Integer> first =
                    executor.submit(registrationRequest);

            Future<Integer> second =
                    executor.submit(registrationRequest);

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both requests should be ready");

            // Release both requests at approximately the same time.
            start.countDown();

            int firstStatus = first.get(30, TimeUnit.SECONDS);
            int secondStatus = second.get(30, TimeUnit.SECONDS);

            int createdCount = 0;
            int conflictCount = 0;

            for (int status : new int[]{firstStatus, secondStatus}) {
                if (status == 201) {
                    createdCount++;
                } else if (status == 409) {
                    conflictCount++;
                } else {
                    fail("Unexpected HTTP status: " + status);
                }
            }

            assertEquals(1, createdCount);
            assertEquals(1, conflictCount);

            List<Customer> matchingCustomers =
                    customerRepository.findAll()
                            .stream()
                            .filter(customer ->
                                    email.equals(customer.getEmail()))
                            .toList();

            assertEquals(
                    1,
                    matchingCustomers.size(),
                    "Only one customer record should exist");

            UserAccount updatedBorrower =
                    userAccountRepository.findById(borrower.getId())
                            .orElseThrow();

            assertEquals(
                    matchingCustomers.get(0).getId(),
                    updatedBorrower.getCustomerId(),
                    "Borrower must link to the single created customer");

        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
