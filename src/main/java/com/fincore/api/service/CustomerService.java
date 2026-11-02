package com.fincore.api.service;

import com.fincore.api.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import com.fincore.api.entity.Customer;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer registerCustomer(
            String fullName,
            String email,
            String phoneNumber) {

        Customer customer = new Customer(UUID.randomUUID());

        customer.setFullName(fullName);
        customer.setEmail(email);
        customer.setPhoneNumber(phoneNumber);

        return customerRepository.save(customer);
    }

    public Customer getCustomerById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"));
    }
}