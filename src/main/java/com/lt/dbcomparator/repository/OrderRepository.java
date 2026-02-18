package com.lt.dbcomparator.repository;

import com.lt.dbcomparator.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
