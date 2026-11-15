package com.casms.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.casms.common.exception.BusinessException;
import com.casms.payment.repository.CurrencyExchangeRateRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class CurrencyExchangeServiceTest {

    @Mock
    private CurrencyExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private CurrencyExchangeService currencyExchangeService;

    @Test
    @DisplayName("Tra cứu danh sách tiền tệ hỗ trợ chuẩn ISO 4217")
    void testGetSupportedCurrencies() {
        List<CurrencyExchangeService.CurrencyMetadata> list = currencyExchangeService.getSupportedCurrencies();
        assertThat(list).isNotEmpty();
        assertThat(list).anyMatch(c -> "USD".equals(c.currencyCode()));
        assertThat(list).anyMatch(c -> "VND".equals(c.currencyCode()));
    }

    @Test
    @DisplayName("Quy đổi hiển thị giữa USD và VND theo tỷ giá mặc định của kho bạc du thuyền")
    void testConvertForDisplay_UsdToVnd() {
        when(exchangeRateRepository.findLatestEffectiveRate(org.mockito.ArgumentMatchers.eq("USD"), org.mockito.ArgumentMatchers.eq("VND"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());

        CurrencyExchangeService.ConversionResult result = currencyExchangeService.convertForDisplay(
                new BigDecimal("100"), "USD", "VND");

        assertThat(result).isNotNull();
        assertThat(result.originalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.convertedAmount()).isEqualByComparingTo(new BigDecimal("2540000"));
        assertThat(result.exchangeRate()).isEqualByComparingTo(new BigDecimal("25400.000000"));
    }

    @Test
    @DisplayName("Từ chối quy đổi tiền tệ thanh toán nếu khách không đồng ý điều khoản DCC")
    void testConvertForSettlement_WithoutConsent_ThrowsDccConsentRequired() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> currencyExchangeService.convertForSettlement(
                        new BigDecimal("100"), "USD", "VND", false));

        assertThat(ex.getCode()).isEqualTo("DCC_CONSENT_REQUIRED");
    }

    @Test
    @DisplayName("Bảo vệ Null-safety: Truyền tiền tệ null ném BusinessException INVALID_CURRENCY thay vì NPE")
    void testConvertForSettlement_NullCurrencies_ThrowsInvalidCurrency() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> currencyExchangeService.convertForSettlement(
                        new BigDecimal("100"), null, null, true));

        assertThat(ex.getCode()).isEqualTo("INVALID_CURRENCY");
    }
}
