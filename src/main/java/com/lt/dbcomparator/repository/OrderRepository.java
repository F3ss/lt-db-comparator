package com.lt.dbcomparator.repository;

import com.lt.dbcomparator.entity.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByCustomerId(String customerId);

    List<Order> findByCustomerIdIn(Collection<String> customerIds);
}
