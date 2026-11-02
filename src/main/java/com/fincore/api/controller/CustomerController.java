package com.fincore.api.controller;

import com.fincore.api.entity.Customer;
import com.fincore.api.service.CustomerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.fincore.api.dto.CustomerRegistrationRequest;
import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer registerCustomer(
            @Valid @RequestBody CustomerRegistrationRequest request) {

        return customerService.registerCustomer(
                request.getFullName(),
                request.getEmail(),
                request.getPhoneNumber());
    }

    @GetMapping("/{id}")
    public Customer getCustomerById(@PathVariable UUID id) {
        return customerService.getCustomerById(id);
    }
}