package com.lt.dbcomparator.service;

import com.lt.dbcomparator.dto.CustomerResponse;
import com.lt.dbcomparator.entity.Customer;
import com.lt.dbcomparator.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Сервис чтения данных клиентов.
 * Адаптирован для MongoDB (CustomerRepository).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Загрузить клиента. В MongoDB документ хранится целиком, поэтому
     * дополнительных fetch не нужно.
     */
    public CustomerResponse getById(String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found: id=" + id));
        return CustomerResponse.from(customer);
    }

    /**
     * Страничная выдача клиентов.
     */
    public Page<CustomerResponse> getAll(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerResponse::from);
    }
}
