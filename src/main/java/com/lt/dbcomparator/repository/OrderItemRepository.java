package com.lt.dbcomparator.repository;

import com.lt.dbcomparator.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
