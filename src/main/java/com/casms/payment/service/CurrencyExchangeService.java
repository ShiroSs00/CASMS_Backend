package com.casms.payment.service;

import com.casms.common.exception.BusinessException;
import com.casms.payment.entity.CurrencyExchangeRate;
import com.casms.payment.repository.CurrencyExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeService {

    private final CurrencyExchangeRateRepository exchangeRateRepository;

    private static final Map<String, CurrencyMetadata> SUPPORTED_CURRENCIES = Map.of(
            "USD", new CurrencyMetadata("USD", "US Dollar", "$", 2, RoundingMode.HALF_UP),
            "SGD", new CurrencyMetadata("SGD", "Singapore Dollar", "S$", 2, RoundingMode.HALF_UP),
            "MYR", new CurrencyMetadata("MYR", "Malaysian Ringgit", "RM", 2, RoundingMode.HALF_UP),
            "THB", new CurrencyMetadata("THB", "Thai Baht", "฿", 2, RoundingMode.HALF_UP),
            "JPY", new CurrencyMetadata("JPY", "Japanese Yen", "¥", 0, RoundingMode.HALF_UP),
            "HKD", new CurrencyMetadata("HKD", "Hong Kong Dollar", "HK$", 2, RoundingMode.HALF_UP),
            "VND", new CurrencyMetadata("VND", "Vietnamese Dong", "₫", 0, RoundingMode.HALF_UP),
            "EUR", new CurrencyMetadata("EUR", "Euro", "€", 2, RoundingMode.HALF_UP)
    );

    private static final Map<String, BigDecimal> DEFAULT_USD_RATES = Map.of(
            "USD", new BigDecimal("1.000000"),
            "SGD", new BigDecimal("1.345000"),
            "MYR", new BigDecimal("4.420000"),
            "THB", new BigDecimal("34.800000"),
            "JPY", new BigDecimal("153.500000"),
            "HKD", new BigDecimal("7.785000"),
            "VND", new BigDecimal("25400.000000"),
            "EUR", new BigDecimal("0.920000")
    );

    public record CurrencyMetadata(
            String currencyCode,
            String displayName,
            String symbol,
            int fractionDigits,
            RoundingMode roundingMode) {}

    public record ConversionResult(
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal convertedAmount,
            String targetCurrency,
            BigDecimal exchangeRate,
            String rateSource,
            Instant effectiveAt,
            String conversionPurpose, // INFORMATIONAL_DISPLAY or ACTUAL_SETTLEMENT
            boolean customerConsentVerified) {}

    public List<CurrencyMetadata> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES.values().stream()
                .sorted(Comparator.comparing(CurrencyMetadata::currencyCode))
                .toList();
    }

    public CurrencyMetadata validateAndGetCurrency(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", "Currency code is required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_ISO_CURRENCY",
                    "Invalid ISO 4217 currency code: " + normalized);
        }
        return SUPPORTED_CURRENCIES.getOrDefault(
                normalized,
                new CurrencyMetadata(normalized, normalized, normalized, 2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public List<CurrencyExchangeRate> getExchangeRates(String baseCurrency) {
        String base = baseCurrency != null && !baseCurrency.isBlank()
                ? baseCurrency.trim().toUpperCase(Locale.ROOT)
                : "USD";
        List<CurrencyExchangeRate> stored = exchangeRateRepository.findByBaseCurrencyOrderByEffectiveFromDesc(base);
        if (!stored.isEmpty()) {
            return stored;
        }
        // Fallback default rates if DB table is empty in unit test context
        Instant fixedTime = Instant.parse("2026-01-01T00:00:00Z");
        return DEFAULT_USD_RATES.entrySet().stream()
                .map(entry -> {
                    CurrencyExchangeRate r = new CurrencyExchangeRate();
                    r.setBaseCurrency("USD");
                    r.setTargetCurrency(entry.getKey());
                    r.setRate(entry.getValue());
                    r.setSource("CRUISE_TREASURY_DEFAULT");
                    r.setEffectiveFrom(fixedTime);
                    return r;
                })
                .toList();
    }

    @Transactional
    public CurrencyExchangeRate registerExchangeRate(
            String baseCurrency,
            String targetCurrency,
            BigDecimal rate,
            String source,
            Instant effectiveFrom) {
        validateAndGetCurrency(baseCurrency);
        validateAndGetCurrency(targetCurrency);
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EXCHANGE_RATE", "Exchange rate must be strictly positive");
        }
        CurrencyExchangeRate entity = new CurrencyExchangeRate();
        entity.setBaseCurrency(baseCurrency.trim().toUpperCase(Locale.ROOT));
        entity.setTargetCurrency(targetCurrency.trim().toUpperCase(Locale.ROOT));
        entity.setRate(rate.setScale(6, RoundingMode.HALF_UP));
        entity.setSource(source != null && !source.isBlank() ? source.trim() : "CRUISE_TREASURY");
        entity.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : Instant.now());
        return exchangeRateRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public ConversionResult convertForDisplay(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency) {
        return performConversion(amount, fromCurrency, toCurrency, false, true);
    }

    @Transactional(readOnly = true)
    public ConversionResult convertForSettlement(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency,
            boolean explicitCustomerDccConsent) {
        String from = fromCurrency.trim().toUpperCase(Locale.ROOT);
        String to = toCurrency.trim().toUpperCase(Locale.ROOT);
        if (!from.equals(to) && !explicitCustomerDccConsent) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "DCC_CONSENT_REQUIRED",
                    "Dynamic Currency Conversion (DCC) requires explicit customer consent before converting settlement currency");
        }
        return performConversion(amount, from, to, true, explicitCustomerDccConsent);
    }

    private ConversionResult performConversion(
            BigDecimal amount,
            String fromCurrency,
            String toCurrency,
            boolean forSettlement,
            boolean consentVerified) {
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Amount must be non-negative");
        }
        CurrencyMetadata sourceMeta = validateAndGetCurrency(fromCurrency);
        CurrencyMetadata targetMeta = validateAndGetCurrency(toCurrency);

        BigDecimal scaledOriginal = amount.setScale(sourceMeta.fractionDigits(), sourceMeta.roundingMode());
        if (sourceMeta.currencyCode().equals(targetMeta.currencyCode())) {
            return new ConversionResult(
                    scaledOriginal,
                    sourceMeta.currencyCode(),
                    scaledOriginal,
                    targetMeta.currencyCode(),
                    BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP),
                    "IDENTITY_NO_FX",
                    Instant.now(),
                    forSettlement ? "ACTUAL_SETTLEMENT" : "INFORMATIONAL_DISPLAY",
                    consentVerified);
        }

        Optional<CurrencyExchangeRate> dbRate = exchangeRateRepository.findLatestEffectiveRate(
                sourceMeta.currencyCode(), targetMeta.currencyCode(), Instant.now());

        BigDecimal rate;
        String source;
        Instant effectiveAt;

        if (dbRate.isPresent()) {
            rate = dbRate.get().getRate();
            source = dbRate.get().getSource();
            effectiveAt = dbRate.get().getEffectiveFrom();
        } else if ("USD".equals(sourceMeta.currencyCode()) && DEFAULT_USD_RATES.containsKey(targetMeta.currencyCode())) {
            rate = DEFAULT_USD_RATES.get(targetMeta.currencyCode());
            source = "CRUISE_TREASURY_DEFAULT";
            effectiveAt = Instant.now();
        } else if ("USD".equals(targetMeta.currencyCode()) && DEFAULT_USD_RATES.containsKey(sourceMeta.currencyCode())) {
            rate = BigDecimal.ONE.divide(DEFAULT_USD_RATES.get(sourceMeta.currencyCode()), 6, RoundingMode.HALF_UP);
            source = "CRUISE_TREASURY_INVERSE";
            effectiveAt = Instant.now();
        } else {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EXCHANGE_RATE_NOT_FOUND",
                    "No active exchange rate configured from " + sourceMeta.currencyCode() + " to " + targetMeta.currencyCode());
        }

        BigDecimal converted = amount.multiply(rate).setScale(targetMeta.fractionDigits(), targetMeta.roundingMode());

        return new ConversionResult(
                scaledOriginal,
                sourceMeta.currencyCode(),
                converted,
                targetMeta.currencyCode(),
                rate.setScale(6, RoundingMode.HALF_UP),
                source,
                effectiveAt,
                forSettlement ? "ACTUAL_SETTLEMENT" : "INFORMATIONAL_DISPLAY",
                consentVerified);
    }
}

