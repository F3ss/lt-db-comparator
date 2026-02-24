package com.lt.dbcomparator.entity;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Профиль клиента — отдельная коллекция в MongoDB.
 */
@Document(collection = "customer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    @Id
    private String id;

    @Indexed
    private String customerId;

    private String avatarUrl;

    private String bio;

    private String preferredLanguage;

    private Boolean notificationsEnabled;

    private String address;

    private String city;

    private String zipCode;
}
