package com.example.shop.customer;

import com.example.shop.common.exception.DuplicateResourceException;
import com.example.shop.common.exception.NotFoundException;
import com.example.shop.customer.dto.CreateCustomerRequest;
import com.example.shop.customer.dto.CustomerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** Registers a customer; e-mail addresses are unique. */
    public CustomerResponse create(CreateCustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Customer", "email", request.email());
        }
        Customer customer = customerRepository.save(new Customer(request.name(), request.email()));
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public Customer getEntity(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }
}
