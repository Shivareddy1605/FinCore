
package com.fincore.api.service;

import com.fincore.api.entity.Customer;
import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.CustomerRepository;
import com.fincore.api.repository.UserAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            UserAccountRepository userAccountRepository) {

        this.customerRepository = customerRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public Customer registerCustomer(
            String fullName,
            String email,
            String phoneNumber) {

        if (customerRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName(fullName);
        customer.setEmail(email);
        customer.setPhoneNumber(phoneNumber);

        return customerRepository.saveAndFlush(customer);
    }

    private void verifyCustomerOwnership(UUID customerId) {

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        // Customer access requires a validated JWT.
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied");
        }

        // The JWT subject identifies the account.
        UUID userId;

        try {
            userId = UUID.fromString(jwtAuth.getToken().getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied");
        }

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Access denied"));

        // Only active borrower accounts may use this flow.
        if (!"ACTIVE".equals(user.getStatus())
                || !"BORROWER".equals(user.getRole())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied");
        }

        // The requested customer must belong to this account.
        if (user.getCustomerId() == null
                || !user.getCustomerId().equals(customerId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied");
        }
    }

    public Customer getCustomerById(UUID id) {

        verifyCustomerOwnership(id);

        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"));
    }

    public Customer updateCustomer(
            UUID id,
            String fullName,
            String email,
            String phoneNumber) {

        // getCustomerById also verifies ownership.
        Customer customer = getCustomerById(id);

        if (!customer.getEmail().equals(email)
                && customerRepository.existsByEmail(email)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        customer.setFullName(fullName);
        customer.setEmail(email);
        customer.setPhoneNumber(phoneNumber);

        return customerRepository.saveAndFlush(customer);
    }
}
