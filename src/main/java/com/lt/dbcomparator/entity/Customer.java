package com.lt.dbcomparator.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ключевая сущность — клиент.
 * В MongoDB хранится как единый документ:
 * {
 * _id: ...,
 * profile: { ... },
 * orders: [
 * { items: [ ... ] }
 * ]
 * }
 */
@Document(collection = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @Indexed
    private String email;

    private String phone;

    private LocalDate dateOfBirth;

    private LocalDateTime registeredAt;

    @Indexed
    private String status;

    private Integer loyaltyPoints;

    private String country;

    // ── Вложенные объекты (Embedded) ──

    private CustomerProfile profile;

    @Builder.Default
    private List<Order> orders = new ArrayList<>();
}
