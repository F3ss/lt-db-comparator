package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Заказ — ManyToOne → Customer, OneToMany → OrderItem.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_customer", columnList = "customer_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_date", columnList = "order_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String shippingAddress;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDate expectedDelivery;

    // ── Связь ──

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<OrderItem> items = new HashSet<>();
}
