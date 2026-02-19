package com.lt.dbcomparator.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Позиция заказа — вложенный объект в Order.
 * Содержит SNAPSHOT данных товара на момент покупки (Denormalization).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    // Ссылка на товар (для аналитики/генерации)
    private String productId;

    // ── Snapshot данных товара (чтобы не делать lookup) ──
    private String productName;
    private String productSku;
    private String productCategory;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalPrice;

    private BigDecimal discount;

    private LocalDateTime createdAt;
}
