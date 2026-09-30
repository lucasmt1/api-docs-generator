package com.example.shop.customer;

import com.example.shop.common.ApiPaths;
import com.example.shop.customer.dto.CreateCustomerRequest;
import com.example.shop.customer.dto.CustomerResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer registration and lookup. */
@RestController
@RequestMapping(ApiPaths.CUSTOMERS)
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.CUSTOMERS + "/" + created.id())).body(created);
    }

    /** Returns the customer record. */
    @GetMapping("/{id}")
    public Customer get(@PathVariable Long id) {
        return customerService.getEntity(id);
    }
}
