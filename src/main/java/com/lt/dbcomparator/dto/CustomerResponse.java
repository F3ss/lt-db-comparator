package com.lt.dbcomparator.dto;

import com.lt.dbcomparator.entity.Customer;
import com.lt.dbcomparator.entity.CustomerProfile;
import com.lt.dbcomparator.entity.Order;
import com.lt.dbcomparator.entity.OrderItem;
import com.lt.dbcomparator.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для ответа GET /api/customers/{id} — Customer с полным графом связей.
 * Используем @Value для иммутабельности (аналог record в Java 11).
 */
@Value
@Schema(description = "Клиент со связанными данными")
public class CustomerResponse {
    Long id;
    String firstName;
    String lastName;
    String email;
    String phone;
    LocalDate dateOfBirth;
    LocalDateTime registeredAt;
    String status;
    Integer loyaltyPoints;
    String country;
    ProfileResponse profile;
    List<OrderResponse> orders;

    public static CustomerResponse from(Customer entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getDateOfBirth(),
                entity.getRegisteredAt(),
                entity.getStatus(),
                entity.getLoyaltyPoints(),
                entity.getCountry(),
                entity.getProfile() != null ? ProfileResponse.from(entity.getProfile()) : null,
                entity.getOrders() != null
                        ? entity.getOrders().stream().map(OrderResponse::from)
                                .collect(java.util.stream.Collectors.toList())
                        : List.of());
    }

    // ── Вложенные DTO ──

    @Value
    @Schema(description = "Профиль клиента")
    public static class ProfileResponse {
        Long id;
        String avatarUrl;
        String bio;
        String preferredLanguage;
        Boolean notificationsEnabled;
        String address;
        String city;
        String zipCode;

        public static ProfileResponse from(CustomerProfile p) {
            return new ProfileResponse(
                    p.getId(), p.getAvatarUrl(), p.getBio(),
                    p.getPreferredLanguage(), p.getNotificationsEnabled(),
                    p.getAddress(), p.getCity(), p.getZipCode());
        }
    }

    @Value
    @Schema(description = "Заказ")
    public static class OrderResponse {
        Long id;
        String orderNumber;
        LocalDateTime orderDate;
        String status;
        BigDecimal totalAmount;
        String currency;
        String shippingAddress;
        String notes;
        LocalDate expectedDelivery;
        List<ItemResponse> items;

        public static OrderResponse from(Order o) {
            return new OrderResponse(
                    o.getId(), o.getOrderNumber(), o.getOrderDate(),
                    o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                    o.getShippingAddress(), o.getNotes(), o.getExpectedDelivery(),
                    o.getItems() != null
                            ? o.getItems().stream().map(ItemResponse::from)
                                    .collect(java.util.stream.Collectors.toList())
                            : List.of());
        }
    }

    @Value
    @Schema(description = "Позиция заказа")
    public static class ItemResponse {
        Long id;
        Integer quantity;
        BigDecimal unitPrice;
        BigDecimal totalPrice;
        BigDecimal discount;
        LocalDateTime createdAt;
        ProductResponse product;

        public static ItemResponse from(OrderItem item) {
            return new ItemResponse(
                    item.getId(), item.getQuantity(),
                    item.getUnitPrice(), item.getTotalPrice(), item.getDiscount(),
                    item.getCreatedAt(),
                    item.getProduct() != null ? ProductResponse.from(item.getProduct()) : null);
        }
    }

    @Value
    @Schema(description = "Товар")
    public static class ProductResponse {
        Long id;
        String name;
        String sku;
        String description;
        BigDecimal price;
        String category;
        Double weight;
        Boolean inStock;

        public static ProductResponse from(Product p) {
            return new ProductResponse(
                    p.getId(), p.getName(), p.getSku(), p.getDescription(),
                    p.getPrice(), p.getCategory(), p.getWeight(), p.getInStock());
        }
    }
}
