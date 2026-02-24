package com.lt.dbcomparator.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/**
 * Ключевая сущность — клиент.
 * В MongoDB теперь хранится в собственной коллекции customers (без вложенных
 * профилей и заказов).
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

}
