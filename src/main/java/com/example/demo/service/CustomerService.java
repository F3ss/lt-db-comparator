package com.example.demo.service;

import com.example.demo.dto.CustomerResponse;
import com.example.demo.entity.Customer;
import com.example.demo.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис чтения данных клиентов.
 * Использует JPA + @EntityGraph для загрузки полного графа связей.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Загрузить клиента со всем графом связей: Profile → Orders → Items → Products.
     */
    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        Customer customer = customerRepository.findWithDetailsById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found: id=" + id));
        return CustomerResponse.from(customer);
    }

    /**
     * Страничная выдача клиентов (без связей — только основные поля).
     */
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAll(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerResponse::from);
    }
}
