package com.lt.dbcomparator.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Ключевая сущность — клиент. Имеет связи:
 * OneToOne → CustomerProfile
 * OneToMany → Order
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customer_email", columnList = "email"),
        @Index(name = "idx_customer_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private LocalDateTime registeredAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Integer loyaltyPoints;

    @Column(length = 60)
    private String country;

    // ── Связи ──

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerProfile profile;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Order> orders = new HashSet<>();
}
