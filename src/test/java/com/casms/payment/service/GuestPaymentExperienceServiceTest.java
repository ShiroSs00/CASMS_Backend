package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.guest.*;
import com.casms.payment.entity.*;
import com.casms.payment.enums.InvoiceDocumentType;
import com.casms.payment.enums.InvoiceStatus;
import com.casms.payment.enums.OnboardAccountStatus;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestPaymentExperienceServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingService bookingService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private OnboardAccountRepository onboardAccountRepository;

    @Mock
    private OnboardTransactionRepository onboardTransactionRepository;

    @Mock
    private OnboardAccountMemberRepository onboardAccountMemberRepository;

    @Mock
    private SpendingLimitRepository spendingLimitRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private FinalSettlementRepository finalSettlementRepository;

    @Mock
    private SepayProperties sepayProperties;

    @InjectMocks
    private GuestPaymentExperienceService guestPaymentExperienceService;

    private Booking mockBooking;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        mockBooking = new Booking();
        mockBooking.setBookingId(bookingId);
        mockBooking.setBookingReference("BK-TEST-2026");
        mockBooking.setBookingStatus(BookingStatus.Draft);
        mockBooking.setAgreedFareAmount(new BigDecimal("25000000"));
        mockBooking.setCurrency("VND");
    }

    @Test
    @DisplayName("Thanh toán thẻ quốc tế thành công và cập nhật đợt thanh toán")
    void testProcessCreditCardPayment_Success() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(mockBooking));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setPaymentId(UUID.randomUUID());
            return p;
        });

        CreditCardPaymentRequest request = new CreditCardPaymentRequest();
        request.setBookingId(bookingId);
        request.setAmount(new BigDecimal("10000000"));
        request.setCardNumber("4111 1111 1111 1111");
        request.setCardHolderName("NGUYEN VAN A");
        request.setExpiryDate("12/28");
        request.setCvv("123");
        request.setCurrency("VND");

        CreditCardPaymentResponse response = guestPaymentExperienceService.processCreditCardPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.Confirmed);
        assertThat(response.getCardBrand()).isEqualTo("VISA");
        assertThat(response.getCardLast4()).isEqualTo("1111");
        assertThat(response.getAuthorizationCode()).startsWith("CC-AUTH-");
        verify(paymentService, times(1)).allocateInstallmentsAndSync(mockBooking);
    }

    @Test
    @DisplayName("Kiểm tra mã khuyến mãi SUMMER2026 giảm 10%")
    void testValidatePromoCode_Summer2026() {
        PromoCodeValidateRequest request = new PromoCodeValidateRequest();
        request.setCode("SUMMER2026");
        request.setOriginalAmount(new BigDecimal("15000000"));

        PromoCodeResponse response = guestPaymentExperienceService.validateAndApplyPromoCode(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("13500000"));
        assertThat(response.getDiscountType()).isEqualTo("PERCENT");
    }

    @Test
    @DisplayName("Kiểm tra mã khuyến mãi CASMSWELCOME giảm 500,000 VNĐ")
    void testValidatePromoCode_CasmsWelcome() {
        PromoCodeValidateRequest request = new PromoCodeValidateRequest();
        request.setCode("CASMSWELCOME");
        request.setOriginalAmount(new BigDecimal("5000000"));

        PromoCodeResponse response = guestPaymentExperienceService.validateAndApplyPromoCode(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("4500000"));
    }

    @Test
    @DisplayName("Mã khuyến mãi không tồn tại trả về invalid")
    void testValidatePromoCode_InvalidCode() {
        PromoCodeValidateRequest request = new PromoCodeValidateRequest();
        request.setCode("FAKECODE99");
        request.setOriginalAmount(new BigDecimal("5000000"));

        PromoCodeResponse response = guestPaymentExperienceService.validateAndApplyPromoCode(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Chia tiền nhóm tạo đúng số lượng phần và mã VietQR tương ứng")
    void testCreateGroupSplitPayment() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(mockBooking));
        when(sepayProperties.getAccountNumber()).thenReturn("0901452366");
        when(sepayProperties.getBankName()).thenReturn("MBBank");
        when(sepayProperties.getAccountName()).thenReturn("CASMS CRUISE");
        when(sepayProperties.getQrTemplate()).thenReturn("compact");

        GroupSplitCreateRequest request = new GroupSplitCreateRequest();
        request.setBookingId(bookingId);
        request.setTotalAmount(new BigDecimal("30000000"));
        request.setNumberOfShares(3);

        GroupSplitResponse response = guestPaymentExperienceService.createGroupSplitPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getNumberOfShares()).isEqualTo(3);
        assertThat(response.getShareAmount()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.getShares()).hasSize(3);
        assertThat(response.getShares().get(0).getQrCodeUrl()).contains("0901452366");
    }

    @Test
    @DisplayName("Xuất hóa đơn đỏ VAT doanh nghiệp chuẩn định dạng thuế")
    void testIssueCorporateVatInvoice() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(mockBooking));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setInvoiceId(UUID.randomUUID());
            return inv;
        });

        CorporateVatInvoiceRequest request = new CorporateVatInvoiceRequest();
        request.setBookingId(bookingId);
        request.setCompanyName("CÔNG TY DU LỊCH VIỆT NAM");
        request.setTaxCode("0312345678");
        request.setCompanyAddress("Quận 1, TP. Hồ Chí Minh");
        request.setVatEmail("ketoan@dulich.vn");

        CorporateVatInvoiceResponse response = guestPaymentExperienceService.issueCorporateVatInvoice(request);

        assertThat(response).isNotNull();
        assertThat(response.getTaxCode()).isEqualTo("0312345678");
        assertThat(response.getInvoiceReference()).startsWith("VAT-");
        assertThat(response.getVatRate()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(response.getDigitalSignatureThumbprint()).startsWith("SHA256:CASMS-EINV-");
    }

    @Test
    @DisplayName("Sao kê chi tiêu trực tiếp cabin (Live Folio Statement)")
    void testGetLiveFolioStatement() {
        UUID accountId = UUID.randomUUID();
        OnboardAccount account = new OnboardAccount();
        account.setOnboardAccountId(accountId);
        account.setStatus(OnboardAccountStatus.ACTIVE);
        account.setCreditLimit(new BigDecimal("20000000"));

        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(onboardTransactionRepository.findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(accountId))
                .thenReturn(Collections.emptyList());

        LiveFolioStatementResponse statement = guestPaymentExperienceService.getLiveFolioStatement(accountId, null);

        assertThat(statement).isNotNull();
        assertThat(statement.getCreditLimit()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(statement.getTotalCharges()).isPositive();
        assertThat(statement.getTransactions()).isNotEmpty();
    }

    @Test
    @DisplayName("Express Check-out tự động tất toán và cấp Thẻ Thông Hành Rời Tàu Gangway")
    void testProcessExpressFolioCheckout() {
        UUID accountId = UUID.randomUUID();
        OnboardAccount account = new OnboardAccount();
        account.setOnboardAccountId(accountId);
        account.setStatus(OnboardAccountStatus.ACTIVE);
        account.setCreditLimit(new BigDecimal("20000000"));

        when(onboardAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpressFolioCheckoutRequest request = new ExpressFolioCheckoutRequest();
        request.setOnboardAccountId(accountId);
        request.setPaymentMethod(PaymentMethod.CreditCard);
        request.setCardLast4("4444");
        request.setConfirmedByGuest(true);

        ExpressFolioCheckoutResponse response = guestPaymentExperienceService.processExpressFolioCheckout(request);

        assertThat(response).isNotNull();
        assertThat(response.getSettlementStatus()).isEqualTo("SETTLED_AND_CLEARED");
        assertThat(response.getGangwayClearancePassToken()).startsWith("GANGWAY-PASS-");
        assertThat(response.getClearanceQrData()).contains("CLEARED_FOR_DEBARKATION");
        assertThat(account.getStatus()).isEqualTo(OnboardAccountStatus.CLOSED);
    }

    @Test
    @DisplayName("Ghi nhận phong tỏa tiền cọc phòng thành công")
    void testRecordSecurityDepositHold() {
        UUID accountId = UUID.randomUUID();
        SecurityDepositHoldRequest request = new SecurityDepositHoldRequest();
        request.setOnboardAccountId(accountId);
        request.setDepositAmount(new BigDecimal("2000000"));
        request.setPaymentMethod(PaymentMethod.CreditCard);
        request.setCardLast4("8888");

        SecurityDepositHoldResponse response = guestPaymentExperienceService.recordSecurityDepositHold(request);

        assertThat(response).isNotNull();
        assertThat(response.getDepositAmount()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(response.getPreAuthCode()).startsWith("PREAUTH-");
        assertThat(response.getStatus()).isEqualTo("HELD_ACTIVE");
    }
}

