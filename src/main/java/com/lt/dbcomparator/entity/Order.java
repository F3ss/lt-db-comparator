package com.lt.dbcomparator.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Заказ — вложенный объект в Customer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String orderNumber;

    private LocalDateTime orderDate;

    private String status;

    private BigDecimal totalAmount;

    private String currency;

    private String shippingAddress;

    private String notes;

    private LocalDate expectedDelivery;

    // ── Вложенные объекты (Embedded) ──

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
