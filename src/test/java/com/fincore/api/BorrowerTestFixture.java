
package com.fincore.api;

import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.UserAccountRepository;
import com.fincore.api.service.JwtService;

import java.util.UUID;

public class BorrowerTestFixture {

    private final UserAccountRepository userAccountRepository;
    private final JwtService jwtService;

    public BorrowerTestFixture(
            UserAccountRepository userAccountRepository,
            JwtService jwtService) {

        this.userAccountRepository = userAccountRepository;
        this.jwtService = jwtService;
    }

    // Create a test borrower linked to an existing customer.
    public String createBorrowerToken(UUID customerId) {

        UserAccount borrower = createBorrower(customerId);

        return jwtService.generateToken(borrower);
    }

    // Create a borrower without a linked customer profile.
    public String createUnlinkedBorrowerToken() {

        UserAccount borrower = createBorrower(null);

        return jwtService.generateToken(borrower);
    }

    private UserAccount createBorrower(UUID customerId) {

        UserAccount borrower = new UserAccount(UUID.randomUUID());

        borrower.setEmail(
                "fixture-" + UUID.randomUUID() + "@example.com"
        );

        // These tests use JWT authentication, not password login.
        borrower.setPasswordHash(
                "test-only-unused-password-hash"
        );

        borrower.setRole("BORROWER");
        borrower.setStatus("ACTIVE");
        borrower.setCustomerId(customerId);

        return userAccountRepository.saveAndFlush(borrower);
    }
}
