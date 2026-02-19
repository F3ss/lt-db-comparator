package com.lt.dbcomparator.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Товар — справочная сущность.
 * Хранится в отдельной коллекции "products".
 */
@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String sku;

    private String description;

    private BigDecimal price;

    @Indexed
    private String category;

    private Double weight;

    private Boolean inStock;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
