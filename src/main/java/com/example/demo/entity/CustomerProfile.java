package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Профиль клиента — OneToOne связь с Customer.
 */
@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(length = 500)
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 10)
    private String preferredLanguage;

    @Column(nullable = false)
    private Boolean notificationsEnabled;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String zipCode;
}
