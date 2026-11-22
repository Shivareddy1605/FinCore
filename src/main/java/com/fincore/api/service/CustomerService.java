
package com.fincore.api.service;

import com.fincore.api.entity.Customer;
import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.CustomerRepository;
import com.fincore.api.repository.UserAccountRepository;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;
    private final EntityManager entityManager;

    public CustomerService(
            CustomerRepository customerRepository,
            UserAccountRepository userAccountRepository,
            EntityManager entityManager) {

        this.customerRepository = customerRepository;
        this.userAccountRepository = userAccountRepository;
        this.entityManager = entityManager;
    }

    private ResponseStatusException accessDenied() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Access denied");
    }

    private UUID getAuthenticatedUserId() {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {
            throw accessDenied();
        }

        try {
            return UUID.fromString(
                    jwtAuth.getToken().getSubject()
            );
        } catch (IllegalArgumentException exception) {
            throw accessDenied();
        }
    }

    private void validateActiveBorrower(UserAccount user) {

        if (!"ACTIVE".equals(user.getStatus())
                || !"BORROWER".equals(user.getRole())) {
            throw accessDenied();
        }
    }

    private UserAccount getActiveBorrower() {

        UUID userId = getAuthenticatedUserId();

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(this::accessDenied);

        validateActiveBorrower(user);

        return user;
    }

    @Transactional
    public Customer registerCustomer(
            String fullName,
            String email,
            String phoneNumber) {

        UUID userId = getAuthenticatedUserId();

        // Lock the account during profile creation.
        UserAccount user = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(this::accessDenied);

        validateActiveBorrower(user);

        if (user.getCustomerId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Customer profile already exists");
        }

        if (!user.getEmail().equalsIgnoreCase(email.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer email must match account email");
        }

        if (customerRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName(fullName);
        customer.setEmail(email);
        customer.setPhoneNumber(phoneNumber);

        // Retain the managed entity returned by JPA.
        customer = customerRepository.saveAndFlush(customer);

        // Load the database-generated creation timestamp.
        entityManager.refresh(customer);

        // Link the customer within the same transaction.
        user.setCustomerId(customer.getId());
        userAccountRepository.saveAndFlush(user);

        return customer;
    }

    private void verifyCustomerOwnership(UUID customerId) {

        UserAccount user = getActiveBorrower();

        if (user.getCustomerId() == null
                || !user.getCustomerId().equals(customerId)) {
            throw accessDenied();
        }
    }

    public Customer getCustomerById(UUID id) {

        verifyCustomerOwnership(id);

        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"));
    }

    @Transactional
    public Customer updateCustomer(
            UUID id,
            String fullName,
            String email,
            String phoneNumber) {

        // Verifies that the authenticated borrower owns the profile.
        Customer customer = getCustomerById(id);

        // Email changes require a separate verification workflow.
        if (!customer.getEmail().equals(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email cannot be changed through profile updates");
        }

        // Only editable profile fields are modified.
        customer.setFullName(fullName);
        customer.setPhoneNumber(phoneNumber);

        return customerRepository.saveAndFlush(customer);
    }
}
