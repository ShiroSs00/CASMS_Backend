package com.casms.ship.repository;

import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.enums.PricingPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruisePricingPolicyRepository extends JpaRepository<CruisePricingPolicy, UUID> {
    @org.springframework.data.jpa.repository.Query(value="SELECT * FROM \"CruisePricingPolicy\" WHERE cruise_id=:cruiseId ORDER BY policy_number DESC LIMIT 1",nativeQuery=true)
    Optional<CruisePricingPolicy> findByCruise_CruiseId(@org.springframework.data.repository.query.Param("cruiseId") UUID cruiseId);
    Optional<CruisePricingPolicy> findByCruise_CruiseIdAndStatus(UUID cruiseId, PricingPolicyStatus status);
    boolean existsByCruise_CruiseIdAndStatus(UUID cruiseId, PricingPolicyStatus status);
    boolean existsByCruise_CruiseId(UUID cruiseId);
    Optional<CruisePricingPolicy> findFirstByOrderByCreatedAtDesc();
}
