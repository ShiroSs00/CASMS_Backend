package com.casms.payment.repository;

import com.casms.payment.entity.CurrencyExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurrencyExchangeRateRepository extends JpaRepository<CurrencyExchangeRate, UUID> {

    @Query("""
            SELECT r FROM CurrencyExchangeRate r
            WHERE UPPER(TRIM(r.baseCurrency)) = UPPER(TRIM(:baseCurrency))
              AND UPPER(TRIM(r.targetCurrency)) = UPPER(TRIM(:targetCurrency))
              AND r.effectiveFrom <= :asOf
              AND (r.effectiveTo IS NULL OR r.effectiveTo > :asOf)
            ORDER BY r.effectiveFrom DESC
            """)
    List<CurrencyExchangeRate> findActiveRates(
            @Param("baseCurrency") String baseCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("asOf") Instant asOf);

    default Optional<CurrencyExchangeRate> findLatestEffectiveRate(
            String baseCurrency, String targetCurrency, Instant asOf) {
        return findActiveRates(baseCurrency, targetCurrency, asOf).stream().findFirst();
    }

    List<CurrencyExchangeRate> findByBaseCurrencyOrderByEffectiveFromDesc(String baseCurrency);
}

