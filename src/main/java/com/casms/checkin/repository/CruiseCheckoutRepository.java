package com.casms.checkin.repository;

import com.casms.checkin.entity.CruiseCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CruiseCheckoutRepository extends JpaRepository<CruiseCheckout, UUID> {
}
