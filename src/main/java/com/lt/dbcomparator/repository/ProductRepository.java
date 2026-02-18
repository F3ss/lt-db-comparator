package com.lt.dbcomparator.repository;

import com.lt.dbcomparator.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
