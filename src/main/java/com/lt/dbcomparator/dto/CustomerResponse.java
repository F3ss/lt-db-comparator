package com.lt.dbcomparator.dto;

import com.lt.dbcomparator.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для ответа GET /api/customers/{id} — Customer с полным графом связей.
 * Используем record для иммутабельности и лаконичности.
 */
@Schema(description = "Клиент со связанными данными")
public record CustomerResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        LocalDateTime registeredAt,
        String status,
        Integer loyaltyPoints,
        String country,
        ProfileResponse profile,
        List<OrderResponse> orders) {

    public static CustomerResponse from(Customer entity, CustomerProfile profile, List<Order> orders,
            java.util.Map<String, List<OrderItem>> itemsByOrderId,
            java.util.Map<String, Product> productMap) {
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
                profile != null ? ProfileResponse.from(profile) : null,
                orders != null
                        ? orders.stream()
                                .map(o -> OrderResponse.from(o, itemsByOrderId.getOrDefault(o.getId(), List.of()),
                                        productMap))
                                .toList()
                        : List.of());
    }

    // ── Вложенные DTO ──

    @Schema(description = "Профиль клиента")
    public record ProfileResponse(
            String avatarUrl,
            String bio,
            String preferredLanguage,
            Boolean notificationsEnabled,
            String address,
            String city,
            String zipCode) {
        public static ProfileResponse from(CustomerProfile p) {
            return new ProfileResponse(
                    p.getAvatarUrl(), p.getBio(),
                    p.getPreferredLanguage(), p.getNotificationsEnabled(),
                    p.getAddress(), p.getCity(), p.getZipCode());
        }
    }

    @Schema(description = "Заказ")
    public record OrderResponse(
            String id,
            String orderNumber,
            LocalDateTime orderDate,
            String status,
            BigDecimal totalAmount,
            String currency,
            String shippingAddress,
            String notes,
            LocalDate expectedDelivery,
            List<ItemResponse> items) {
        public static OrderResponse from(Order o, List<OrderItem> items, java.util.Map<String, Product> productMap) {
            return new OrderResponse(
                    o.getId(), o.getOrderNumber(), o.getOrderDate(),
                    o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                    o.getShippingAddress(), o.getNotes(), o.getExpectedDelivery(),
                    items != null
                            ? items.stream().map(i -> ItemResponse.from(i, productMap.get(i.getProductId()))).toList()
                            : List.of());
        }
    }

    @Schema(description = "Позиция заказа")
    public record ItemResponse(
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal discount,
            LocalDateTime createdAt,
            ProductResponse product) {
        public static ItemResponse from(OrderItem item, Product product) {
            return new ItemResponse(
                    item.getQuantity(),
                    item.getUnitPrice(), item.getTotalPrice(), item.getDiscount(),
                    item.getCreatedAt(),
                    product != null ? ProductResponse.from(product) : null);
        }
    }

    @Schema(description = "Товар")
    public record ProductResponse(
            String id,
            String name,
            String sku,
            String description,
            BigDecimal price,
            String category,
            Double weight,
            Boolean inStock) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(
                    p.getId(), p.getName(), p.getSku(), p.getDescription(),
                    p.getPrice(), p.getCategory(), p.getWeight(), p.getInStock());
        }
    }
}
