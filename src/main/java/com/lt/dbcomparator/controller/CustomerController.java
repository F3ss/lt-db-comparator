package com.lt.dbcomparator.controller;

import com.lt.dbcomparator.dto.CustomerResponse;
import com.lt.dbcomparator.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Чтение данных клиентов — для нагрузочного тестирования на чтение.
 */
@Tag(name = "Customers", description = "Чтение клиентов со связанными сущностями")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Получить клиента по ID", description = "Возвращает клиента со всем графом: Profile, Orders → Items → Products.")
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(
            @Parameter(description = "ID клиента", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(customerService.getById(id));
    }

    @Operation(summary = "Страничная выдача клиентов", description = "Клиенты без связей. Используйте параметры page и size.")
    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getAll(
            @PageableDefault(page = 0, size = 20) Pageable pageable) {
        return ResponseEntity.ok(customerService.getAll(pageable));
    }
}
