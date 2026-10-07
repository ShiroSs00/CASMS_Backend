package com.casms.payment.repository;

import com.casms.payment.entity.SpendingLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpendingLimitRepository extends JpaRepository<SpendingLimit, UUID> {
}
