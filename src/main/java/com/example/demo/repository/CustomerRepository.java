package com.example.demo.repository;

import com.example.demo.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Загрузка клиента со всеми связями одним запросом (JOIN FETCH).
     */
    @EntityGraph(attributePaths = { "profile", "orders", "orders.items", "orders.items.product" })
    Optional<Customer> findWithDetailsById(Long id);

    /**
     * Страничная выдача (без загрузки связей — только Customer).
     */
    Page<Customer> findAll(Pageable pageable);
}
