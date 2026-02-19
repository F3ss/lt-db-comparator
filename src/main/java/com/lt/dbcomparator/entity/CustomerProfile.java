package com.lt.dbcomparator.entity;

import lombok.*;

/**
 * Профиль клиента — вложенный объект в Customer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    private String avatarUrl;

    private String bio;

    private String preferredLanguage;

    private Boolean notificationsEnabled;

    private String address;

    private String city;

    private String zipCode;
}
