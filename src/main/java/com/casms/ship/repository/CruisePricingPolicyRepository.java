package com.casms.ship.repository;

import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.enums.PricingPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruisePricingPolicyRepository extends JpaRepository<CruisePricingPolicy, UUID> {
    Optional<CruisePricingPolicy> findByCruise_CruiseId(UUID cruiseId);
    Optional<CruisePricingPolicy> findByCruise_CruiseIdAndStatus(UUID cruiseId, PricingPolicyStatus status);
    boolean existsByCruise_CruiseIdAndStatus(UUID cruiseId, PricingPolicyStatus status);
    boolean existsByCruise_CruiseId(UUID cruiseId);
    Optional<CruisePricingPolicy> findFirstByOrderByCreatedAtDesc();
}
