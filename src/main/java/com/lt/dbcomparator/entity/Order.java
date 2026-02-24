package com.lt.dbcomparator.entity;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Заказ — отдельная коллекция в MongoDB.
 */
@Document(collection = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Indexed
    private String customerId;

    private String orderNumber;

    private LocalDateTime orderDate;

    private String status;

    private BigDecimal totalAmount;

    private String currency;

    private String shippingAddress;

    private String notes;

    private LocalDate expectedDelivery;

}
