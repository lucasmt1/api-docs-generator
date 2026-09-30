package com.example.shop.customer.dto;

import com.example.shop.customer.Customer;

public record CustomerResponse(Long id, String name, String email) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail());
    }
}
