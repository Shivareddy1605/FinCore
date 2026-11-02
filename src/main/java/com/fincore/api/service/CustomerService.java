package com.fincore.api.service;

import com.fincore.api.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import com.fincore.api.entity.Customer;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer registerCustomer() {
    Customer customer = new Customer(UUID.randomUUID());
    return customerRepository.save(customer);
}
}