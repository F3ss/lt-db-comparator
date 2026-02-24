package com.lt.dbcomparator.repository;

import com.lt.dbcomparator.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
}
