package com.lt.dbcomparator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lt.dbcomparator.dto.CustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Сервис чтения данных клиентов.
 * <p>
 * getById() оптимизирован: использует нативный SQL c json_build_object
 * (PostgreSQL),
 * чтобы переложить сборку графа объектов на БД и избежать накладных расходов
 * JPA.
 * <p>
 * getAll() оптимизирован: использует простой JDBC SELECT c LIMIT/OFFSET,
 * чтобы избежать overhead JPA (lazy loading proxies, dirty checking, session
 * management).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Загрузить клиента со всем графом связей: Profile → Orders → Items → Products.
     * <p>
     * Выполняется один SQL-запрос, который возвращает JSON.
     * Это минимизирует overhead приложения и CPU на маппинг сущностей.
     */
    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        String sql = """
                SELECT json_build_object(
                    'id', c.id,
                    'firstName', c.first_name,
                    'lastName', c.last_name,
                    'email', c.email,
                    'phone', c.phone,
                    'dateOfBirth', c.date_of_birth,
                    'registeredAt', c.registered_at,
                    'status', c.status,
                    'loyaltyPoints', c.loyalty_points,
                    'country', c.country,
                    'profile', (
                        SELECT json_build_object(
                            'id', p.id,
                            'avatarUrl', p.avatar_url,
                            'bio', p.bio,
                            'preferredLanguage', p.preferred_language,
                            'notificationsEnabled', p.notifications_enabled,
                            'address', p.address,
                            'city', p.city,
                            'zipCode', p.zip_code
                        ) FROM customer_profiles p WHERE p.customer_id = c.id
                    ),
                    'orders', COALESCE((
                        SELECT json_agg(
                            json_build_object(
                                'id', o.id,
                                'orderNumber', o.order_number,
                                'orderDate', o.order_date,
                                'status', o.status,
                                'totalAmount', o.total_amount,
                                'currency', o.currency,
                                'shippingAddress', o.shipping_address,
                                'notes', o.notes,
                                'expectedDelivery', o.expected_delivery,
                                'items', COALESCE((
                                    SELECT json_agg(
                                        json_build_object(
                                            'id', oi.id,
                                            'quantity', oi.quantity,
                                            'unitPrice', oi.unit_price,
                                            'totalPrice', oi.total_price,
                                            'discount', oi.discount,
                                            'createdAt', oi.created_at,
                                            'product', (
                                                SELECT json_build_object(
                                                    'id', pr.id,
                                                    'name', pr.name,
                                                    'sku', pr.sku,
                                                    'description', pr.description,
                                                    'price', pr.price,
                                                    'category', pr.category,
                                                    'weight', pr.weight,
                                                    'inStock', pr.in_stock
                                                ) FROM products pr WHERE pr.id = oi.product_id
                                            )
                                        )
                                    ) FROM order_items oi WHERE oi.order_id = o.id
                                ), '[]'::json)
                            )
                        ) FROM orders o WHERE o.customer_id = c.id
                    ), '[]'::json)
                )
                FROM customers c
                WHERE c.id = ?
                """;

        try {
            String json = jdbcTemplate.queryForObject(sql, String.class, id);
            return objectMapper.readValue(json, CustomerResponse.class);
        } catch (EmptyResultDataAccessException e) {
            throw new RuntimeException("Customer not found: id=" + id, e);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON from DB for customer id={}", id, e);
            throw new RuntimeException("Error parsing data", e);
        }
    }

    /**
     * Страничная выдача клиентов (без связей — только основные поля).
     * Использует чистый JDBC для максимальной производительности.
     */
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAll(Pageable pageable) {
        // 1. Считаем общее кол-во (можно оптимизировать, используя estimate row count)
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Long.class);
        long total = count != null ? count : 0;

        // 2. Выбираем страницу
        String sql = """
                SELECT id, first_name, last_name, email, phone, date_of_birth,
                       registered_at, status, loyalty_points, country
                FROM customers
                ORDER BY id
                LIMIT ? OFFSET ?
                """;

        List<CustomerResponse> customers = jdbcTemplate.query(
                sql,
                new CustomerRowMapper(),
                pageable.getPageSize(),
                pageable.getOffset());

        return new PageImpl<>(customers, pageable, total);
    }

    private static class CustomerRowMapper implements RowMapper<CustomerResponse> {
        @Override
        public CustomerResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CustomerResponse(
                    rs.getLong("id"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getObject("date_of_birth", java.time.LocalDate.class),
                    rs.getObject("registered_at", java.time.LocalDateTime.class),
                    rs.getString("status"),
                    rs.getInt("loyalty_points"),
                    rs.getString("country"),
                    null, // Profile загружается отдельно или игнорируется для списка
                    List.of() // Orders игнорируются для списка
            );
        }
    }
}
