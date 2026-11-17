
package com.fincore.api.controller;

import com.fincore.api.entity.Customer;
import com.fincore.api.service.CustomerService;
import com.fincore.api.dto.CustomerRegistrationRequest;
import com.fincore.api.dto.CustomerUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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

    @PutMapping("/{id}")
    public Customer updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerUpdateRequest request) {

        return customerService.updateCustomer(
                id,
                request.getFullName(),
                request.getEmail(),
                request.getPhoneNumber());
    }
}
