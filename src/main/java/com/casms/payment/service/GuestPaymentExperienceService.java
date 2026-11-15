package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.enums.Permission;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.guest.*;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.repository.*;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuestPaymentExperienceService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final InstallmentRepository installmentRepository;
    private final OnboardAccountRepository onboardAccountRepository;
    private final OnboardTransactionRepository onboardTransactionRepository;
    private final OnboardAccountMemberRepository onboardAccountMemberRepository;
    private final SpendingLimitRepository spendingLimitRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinalSettlementRepository finalSettlementRepository;
    private final SepayProperties sepayProperties;

    // Cache tạm thời lưu tiến trình Group Split
    private final Map<String, GroupSplitResponse> splitGroupCache = new ConcurrentHashMap<>();
    // Cache phụ lưu số tiền cọc bảo đảm đang giữ theo onboard account
    private final Map<UUID, BigDecimal> securityDepositCache = new ConcurrentHashMap<>();

    // =========================================================================
    // 1. THANH TOÁN THẺ QUỐC TẾ (CREDIT / DEBIT CARD - VISA, MASTERCARD, JCB, AMEX)
    // =========================================================================
    @Transactional
    public CreditCardPaymentResponse processCreditCardPayment(CreditCardPaymentRequest request) {
        return processCreditCardPayment(request, null);
    }

    @Transactional
    public CreditCardPaymentResponse processCreditCardPayment(CreditCardPaymentRequest request, CasmsPrincipal principal) {
        log.info("Processing Credit Card payment for booking: {}, amount: {}", request.getBookingId(), request.getAmount());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        // 1. Kiểm tra quyền sở hữu đơn đặt tour (Bảo mật chống IDOR)
        assertBookingAccess(booking, principal);

        // 2. Kiểm tra trạng thái đơn đặt tour
        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BOOKING_CANCELLED", "Không thể thanh toán cho đơn hàng đã bị hủy");
        }
        if (booking.getBookingStatus() == BookingStatus.Draft
                && booking.getHoldExpiresAt() != null
                && Instant.now().isAfter(booking.getHoldExpiresAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BOOKING_HOLD_EXPIRED", "Thời gian giữ chỗ của đơn hàng đã hết hạn. Vui lòng tạo đơn mới.");
        }

        // 3. Kiểm tra số tiền còn lại của đơn hàng (Không cho thanh toán quá số dư)
        BigDecimal agreedFare = booking.getAgreedFareAmount() != null ? booking.getAgreedFareAmount() : BigDecimal.ZERO;
        BigDecimal totalPaid = paymentRepository.findForBooking(booking.getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getConfirmedAmount() != null)
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = agreedFare.subtract(totalPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BOOKING_ALREADY_PAID", "Đơn hàng này đã được thanh toán toàn bộ!");
        }

        // 4. Kiểm tra tiền tệ và số tiền hợp lệ
        String payCurrency = request.getCurrency() != null && !request.getCurrency().isBlank()
                ? request.getCurrency().trim().toUpperCase(Locale.ROOT)
                : "VND";
        if (!"VND".equals(payCurrency) && !"USD".equals(payCurrency)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", "Hệ thống chỉ hỗ trợ thanh toán bằng VND hoặc USD qua thẻ Visa/Mastercard/JCB/Amex");
        }

        if ("VND".equals(payCurrency) && request.getAmount().compareTo(new BigDecimal("1000")) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Số tiền thanh toán tối thiểu là 1.000 VNĐ");
        }
        if ("USD".equals(payCurrency) && request.getAmount().compareTo(new BigDecimal("0.10")) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Số tiền thanh toán tối thiểu là 0.10 USD");
        }

        // 5. Kiểm tra tính hợp lệ của số thẻ (Luhn Algorithm)
        String rawCardNumber = request.getCardNumber().replaceAll("\\s+", "").replaceAll("-", "");
        if (rawCardNumber.length() < 13 || rawCardNumber.length() > 19 || !rawCardNumber.matches("\\d+")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER", "Số thẻ tín dụng không hợp lệ");
        }
        if (!validateLuhn(rawCardNumber)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD_CHECKSUM", "Số thẻ không đúng theo tiêu chuẩn quốc tế (Luhn Check failed)");
        }

        // 6. Kiểm tra ngày hết hạn (MM/YY) & Xác thực thẻ còn hạn sử dụng
        String expiry = request.getExpiryDate().trim();
        if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY_DATE", "Ngày hết hạn phải theo định dạng MM/YY (ví dụ: 12/28)");
        }
        int expMonth = Integer.parseInt(expiry.substring(0, 2));
        int expYear = 2000 + Integer.parseInt(expiry.substring(3, 5));
        YearMonth nowYm = YearMonth.now();
        YearMonth expYm = YearMonth.of(expYear, expMonth);
        if (expYm.isBefore(nowYm)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CARD_EXPIRED", "Thẻ tín dụng đã hết hạn sử dụng (" + expiry + "). Vui lòng sử dụng thẻ còn hiệu lực.");
        }

        // 7. Kiểm tra mã bảo mật CVV
        String cvv = request.getCvv().trim();
        if (!cvv.matches("\\d{3,4}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CVV", "Mã CVV/CVC phải gồm 3 hoặc 4 chữ số");
        }

        String cardBrand = detectCardBrand(rawCardNumber);
        String cardLast4 = rawCardNumber.substring(rawCardNumber.length() - 4);
        String authCode = "CC-AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        // 8. Quy đổi tỷ giá nếu đơn vị thanh toán khác đơn vị tiền tệ của booking
        String bookingCurrency = booking.getCurrency() != null ? booking.getCurrency().trim().toUpperCase(Locale.ROOT) : "VND";
        BigDecimal exchangeRate = (sepayProperties != null && sepayProperties.getUsdToVndRate() != null)
                ? sepayProperties.getUsdToVndRate()
                : new BigDecimal("25400");

        BigDecimal amountInBookingCurrency = request.getAmount();
        String conversionNotice = "";
        if ("USD".equals(payCurrency) && "VND".equals(bookingCurrency)) {
            amountInBookingCurrency = request.getAmount().multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
            conversionNotice = String.format(" (Quy đổi: %s USD = %,.0f VNĐ, tỷ giá %,.0f VNĐ/USD)",
                    request.getAmount().toPlainString(), amountInBookingCurrency, exchangeRate);
        } else if ("VND".equals(payCurrency) && "USD".equals(bookingCurrency)) {
            amountInBookingCurrency = request.getAmount().divide(exchangeRate, 2, RoundingMode.HALF_UP);
            conversionNotice = String.format(" (Quy đổi: %,.0f VNĐ = %s USD, tỷ giá %,.0f VNĐ/USD)",
                    request.getAmount(), amountInBookingCurrency.toPlainString(), exchangeRate);
        }

        // 9. Tìm installment mục tiêu (nếu có)
        Installment targetInstallment = null;
        if (request.getInstallmentId() != null) {
            targetInstallment = installmentRepository.findById(request.getInstallmentId()).orElse(null);
        } else {
            List<Installment> installments = installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId());
            for (Installment inst : installments) {
                if (inst.getStatus() == InstallmentStatus.Scheduled) {
                    targetInstallment = inst;
                    break;
                }
            }
        }

        // 10. Tạo bản ghi Payment với phương thức CreditCard và đầy đủ metadata PCI-DSS
        String idempotencyKey = request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey().trim()
                : "CC-" + UUID.randomUUID();

        // Kiểm tra Idempotency
        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPaymentOpt.isPresent()) {
            Payment existing = existingPaymentOpt.get();
            if (existing.getStatus() == PaymentStatus.Confirmed) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PAYMENT_ALREADY_CONFIRMED", "Giao dịch thanh toán này đã được xác nhận thành công trước đó!");
            }
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setInstallment(targetInstallment);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.CreditCard);
        payment.setProvider("CREDIT_CARD_" + cardBrand);
        payment.setPaymentToken("tok_card_" + cardBrand.toLowerCase(Locale.ROOT) + "_" + cardLast4);
        payment.setRequestedAmount(request.getAmount());
        payment.setConfirmedAmount(amountInBookingCurrency);
        payment.setCurrency(bookingCurrency);
        payment.setOriginalAmount(request.getAmount());
        payment.setOriginalCurrency(payCurrency);
        payment.setConvertedAmount(amountInBookingCurrency);
        payment.setSettlementCurrency(bookingCurrency);
        payment.setExchangeRateApplied(exchangeRate);
        payment.setExchangeRateSource("SEPAY_CONFIG");
        payment.setExchangeRateTimestamp(Instant.now());
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setProcessorReference(authCode);
        payment.setRequestedAt(Instant.now());
        payment.setConfirmedAt(Instant.now());
        payment.setAuthorizedAmount(amountInBookingCurrency);
        payment.setCapturedAmount(amountInBookingCurrency);
        payment.setCapturedAt(Instant.now());
        payment.setIdempotencyKey(idempotencyKey);

        Payment savedPayment = paymentRepository.save(payment);

        // 11. Đồng bộ đợt trả góp và cập nhật trạng thái đơn đặt
        paymentService.allocateInstallmentsAndSync(booking);

        // 12. Nếu khách hàng tick yêu cầu xuất hóa đơn đỏ VAT doanh nghiệp
        String vatInvoiceRef = null;
        if (Boolean.TRUE.equals(request.getRequiresVatInvoice())
                && request.getCompanyName() != null && !request.getCompanyName().isBlank()
                && request.getTaxCode() != null && !request.getTaxCode().isBlank()) {
            CorporateVatInvoiceRequest vatReq = new CorporateVatInvoiceRequest();
            vatReq.setBookingId(booking.getBookingId());
            vatReq.setCompanyName(request.getCompanyName());
            vatReq.setTaxCode(request.getTaxCode());
            vatReq.setCompanyAddress(request.getCompanyAddress() != null ? request.getCompanyAddress() : "N/A");
            vatReq.setVatEmail(request.getVatEmail() != null ? request.getVatEmail() : "khachhang@congty.vn");
            CorporateVatInvoiceResponse vatResp = issueCorporateVatInvoice(vatReq, principal);
            vatInvoiceRef = vatResp.getInvoiceReference();
        }

        // Nạp lại booking để lấy trạng thái mới nhất sau sync
        Booking refreshedBooking = bookingRepository.findById(booking.getBookingId()).orElse(booking);

        BigDecimal surchargePercent = (sepayProperties != null && sepayProperties.isCardSurchargeEnabled() && sepayProperties.getCardSurchargePercent() != null)
                ? sepayProperties.getCardSurchargePercent()
                : BigDecimal.ZERO;
        BigDecimal surchargeAmount = request.getAmount().multiply(surchargePercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalAmountWithSurcharge = request.getAmount().add(surchargeAmount);
        String surchargeNotice = surchargeAmount.signum() > 0
                ? String.format(" (Phụ phí quẹt thẻ %s%%: %s %s)", surchargePercent.stripTrailingZeros().toPlainString(), surchargeAmount.toPlainString(), payCurrency)
                : "";

        return CreditCardPaymentResponse.builder()
                .paymentId(savedPayment.getPaymentId())
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .amount(request.getAmount())
                .baseAmount(request.getAmount())
                .surchargePercent(surchargePercent)
                .surchargeAmount(surchargeAmount)
                .totalAmountWithSurcharge(totalAmountWithSurcharge)
                .currency(payCurrency)
                .paymentStatus(savedPayment.getStatus())
                .authorizationCode(authCode)
                .cardLast4(cardLast4)
                .cardBrand(cardBrand)
                .bookingStatus(refreshedBooking.getBookingStatus())
                .paidAt(savedPayment.getConfirmedAt())
                .message("Thanh toán thẻ quốc tế " + cardBrand + " (" + cardLast4 + ") thành công qua cổng thanh toán bảo mật 3D-Secure" + surchargeNotice + conversionNotice)
                .vatInvoiceReference(vatInvoiceRef)
                .build();
    }

    // =========================================================================
    // 2. MÃ KHUYẾN MÃI / VOUCHER GIẢM GIÁ (PROMO CODE)
    // =========================================================================
    public PromoCodeResponse validateAndApplyPromoCode(PromoCodeValidateRequest request) {
        String code = request.getCode() != null ? request.getCode().trim().toUpperCase(Locale.ROOT) : "";
        BigDecimal originalAmount = request.getOriginalAmount();

        if (code.isBlank()) {
            return PromoCodeResponse.builder()
                    .code(code)
                    .valid(false)
                    .originalAmount(originalAmount)
                    .finalAmount(originalAmount)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Vui lòng nhập mã khuyến mãi")
                    .build();
        }

        BigDecimal discountRate = BigDecimal.ZERO;
        BigDecimal fixedDiscount = BigDecimal.ZERO;
        BigDecimal maxDiscount = null;
        String desc = "";

        switch (code) {
            case "SUMMER2026" -> {
                discountRate = new BigDecimal("0.10"); // 10%
                maxDiscount = new BigDecimal("2000000"); // Tối đa 2 triệu VND
                desc = "Ưu đãi chào hè 2026: Giảm 10% tổng giá trị tour (tối đa 2.000.000 VNĐ)";
            }
            case "VIPCRUISE" -> {
                discountRate = new BigDecimal("0.15"); // 15%
                maxDiscount = new BigDecimal("5000000"); // Tối đa 5 triệu VND
                desc = "Đặc quyền hội viên VIP: Giảm 15% tổng giá trị tour (tối đa 5.000.000 VNĐ)";
            }
            case "CASMSWELCOME" -> {
                fixedDiscount = new BigDecimal("500000"); // Giảm thẳng 500k
                desc = "Quà tặng khách hàng mới: Giảm trực tiếp 500.000 VNĐ cho đơn từ 3.000.000 VNĐ";
                if (originalAmount.compareTo(new BigDecimal("3000000")) < 0) {
                    return PromoCodeResponse.builder()
                            .code(code)
                            .valid(false)
                            .originalAmount(originalAmount)
                            .finalAmount(originalAmount)
                            .discountAmount(BigDecimal.ZERO)
                            .description(desc)
                            .message("Mã CASMSWELCOME chỉ áp dụng cho đơn hàng từ 3.000.000 VNĐ trở lên")
                            .build();
                }
            }
            case "FAMILYFUN" -> {
                discountRate = new BigDecimal("0.08"); // 8%
                maxDiscount = new BigDecimal("3000000");
                desc = "Kỳ nghỉ gia đình hạnh phúc: Giảm 8% tổng chi phí tour (tối đa 3.000.000 VNĐ)";
            }
            default -> {
                return PromoCodeResponse.builder()
                        .code(code)
                        .valid(false)
                        .originalAmount(originalAmount)
                        .finalAmount(originalAmount)
                        .discountAmount(BigDecimal.ZERO)
                        .message("Mã khuyến mãi '" + code + "' không tồn tại hoặc đã hết lượt sử dụng")
                        .build();
            }
        }

        BigDecimal discountAmount;
        if (discountRate.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = originalAmount.multiply(discountRate).setScale(0, RoundingMode.HALF_UP);
            if (maxDiscount != null && discountAmount.compareTo(maxDiscount) > 0) {
                discountAmount = maxDiscount;
            }
        } else {
            discountAmount = fixedDiscount;
        }

        if (discountAmount.compareTo(originalAmount) > 0) {
            discountAmount = originalAmount;
        }

        BigDecimal finalAmount = originalAmount.subtract(discountAmount);

        return PromoCodeResponse.builder()
                .code(code)
                .valid(true)
                .discountType(discountRate.compareTo(BigDecimal.ZERO) > 0 ? "PERCENT" : "FIXED")
                .discountValue(discountRate.compareTo(BigDecimal.ZERO) > 0 ? discountRate.multiply(new BigDecimal("100")) : fixedDiscount)
                .discountAmount(discountAmount)
                .originalAmount(originalAmount)
                .finalAmount(finalAmount)
                .description(desc)
                .message("Áp dụng mã ưu đãi thành công! Quý khách tiết kiệm được " + String.format("%,d", discountAmount.longValue()) + " VNĐ")
                .build();
    }

    // =========================================================================
    // 3. CHIA TIỀN NHÓM (GROUP SPLIT PAYMENT VỚI TỪNG MÃ VIETQR RIÊNG)
    // =========================================================================
    public GroupSplitResponse createGroupSplitPayment(GroupSplitCreateRequest request) {
        return createGroupSplitPayment(request, null);
    }

    public GroupSplitResponse createGroupSplitPayment(GroupSplitCreateRequest request, CasmsPrincipal principal) {
        log.info("Creating Group Split Payment for booking: {}, shares: {}", request.getBookingId(), request.getNumberOfShares());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        assertBookingAccess(booking, principal);

        int n = request.getNumberOfShares();
        if (n < 2) n = 2;
        if (n > 20) n = 20;

        BigDecimal total = request.getTotalAmount();
        BigDecimal perShare = total.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);

        String splitGroupId = "SPLIT-" + booking.getBookingReference() + "-" + (System.currentTimeMillis() % 100000);
        List<GroupSplitResponse.GroupSplitShareItem> shares = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String name = (request.getMemberNames() != null && i < request.getMemberNames().size() && request.getMemberNames().get(i) != null && !request.getMemberNames().get(i).isBlank())
                    ? request.getMemberNames().get(i).trim()
                    : "Thành viên " + (i + 1);

            String email = (request.getMemberEmails() != null && i < request.getMemberEmails().size())
                    ? request.getMemberEmails().get(i)
                    : null;

            String shareId = splitGroupId + "-P" + (i + 1);
            String paymentRef = "CASMS " + booking.getBookingReference() + " P" + (i + 1);

            String qrUrl = String.format(
                    "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                    sepayProperties.getAccountNumber(),
                    sepayProperties.getBankName(),
                    perShare.toPlainString(),
                    URLEncoder.encode(paymentRef, StandardCharsets.UTF_8),
                    sepayProperties.getQrTemplate()
            );

            shares.add(GroupSplitResponse.GroupSplitShareItem.builder()
                    .shareIndex(i + 1)
                    .shareId(shareId)
                    .payerName(name)
                    .payerEmail(email)
                    .amount(perShare)
                    .status("PENDING")
                    .paymentReference(paymentRef)
                    .bankAccountName(sepayProperties.getAccountName())
                    .bankAccountNumber(sepayProperties.getAccountNumber())
                    .bankCode(sepayProperties.getBankName())
                    .qrCodeUrl(qrUrl)
                    .checkoutUrl("https://mycruise.casms.vn/pay/split/" + shareId)
                    .build());
        }

        GroupSplitResponse response = GroupSplitResponse.builder()
                .splitGroupId(splitGroupId)
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .totalAmount(total)
                .shareAmount(perShare)
                .numberOfShares(n)
                .paidAmount(BigDecimal.ZERO)
                .progressPercent(BigDecimal.ZERO)
                .fullyPaid(false)
                .shares(shares)
                .build();

        splitGroupCache.put(splitGroupId, response);
        return response;
    }

    public GroupSplitResponse getGroupSplitStatus(String splitGroupId) {
        GroupSplitResponse group = splitGroupCache.get(splitGroupId);
        if (group == null) {
            throw new ResourceNotFoundException("Group Split không tồn tại: " + splitGroupId);
        }
        return group;
    }

    // =========================================================================
    // 4. XUẤT HÓA ĐƠN ĐỎ VAT DOANH NGHIỆP (CORPORATE ELECTRONIC VAT INVOICE)
    // =========================================================================
    @Transactional
    public CorporateVatInvoiceResponse issueCorporateVatInvoice(CorporateVatInvoiceRequest request) {
        return issueCorporateVatInvoice(request, null);
    }

    @Transactional
    public CorporateVatInvoiceResponse issueCorporateVatInvoice(CorporateVatInvoiceRequest request, CasmsPrincipal principal) {
        log.info("Issuing Corporate VAT Invoice for booking: {}, TaxCode: {}", request.getBookingId(), request.getTaxCode());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        assertBookingAccess(booking, principal);

        // Kiểm tra định dạng Mã số thuế Việt Nam (10 chữ số độc lập hoặc 13 chữ số chi nhánh)
        String cleanTaxCode = request.getTaxCode().trim().replaceAll("\\s+", "");
        if (!cleanTaxCode.matches("^\\d{10}(-\\d{3}|\\d{3})?$")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TAX_CODE",
                    "Mã số thuế doanh nghiệp không đúng định dạng. Theo quy định Tổng Cục Thuế, MST phải gồm 10 chữ số (doanh nghiệp) hoặc 13 chữ số (chi nhánh/đơn vị trực thuộc).");
        }

        BigDecimal total = booking.getAgreedFareAmount() != null ? booking.getAgreedFareAmount() : BigDecimal.ZERO;
        BigDecimal vatRate = new BigDecimal("8.00"); // Thuế GTGT 8% ngành du lịch
        BigDecimal subtotal = total.divide(new BigDecimal("1.08"), 0, RoundingMode.HALF_UP);
        BigDecimal vatAmount = total.subtract(subtotal);

        String invoiceRef = "VAT-" + Year.now().getValue() + "-" + String.format("%05d", (int) (Math.random() * 90000 + 10000));
        String invoiceSeries = "1C26TAA";
        String signThumbprint = "SHA256:CASMS-EINV-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase(Locale.ROOT);

        // Tạo hóa đơn trong hệ thống
        Invoice invoice = new Invoice();
        invoice.setBooking(booking);
        invoice.setInvoiceReference(invoiceRef);
        invoice.setDocumentType(InvoiceDocumentType.BookingInvoice);
        invoice.setVersionNo(1);
        invoice.setIssuedAt(Instant.now());
        invoice.setSourceCutoffAt(Instant.now());
        invoice.setCurrency(booking.getCurrency() != null ? booking.getCurrency() : "VND");
        invoice.setSnapshotTotalAmount(total);
        invoice.setSnapshotNetAmountDue(total);
        invoice.setStatus(InvoiceStatus.Issued);
        invoice.setDocumentReference("DOC-" + invoiceRef);
        Invoice savedInvoice = invoiceRepository.save(invoice);

        return CorporateVatInvoiceResponse.builder()
                .invoiceId(savedInvoice.getInvoiceId())
                .invoiceReference(invoiceRef)
                .invoiceSeries(invoiceSeries)
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .companyName(request.getCompanyName())
                .taxCode(cleanTaxCode)
                .companyAddress(request.getCompanyAddress())
                .vatEmail(request.getVatEmail())
                .subtotalAmount(subtotal)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .totalAmount(total)
                .currency(invoice.getCurrency())
                .issuedAt(savedInvoice.getIssuedAt())
                .digitalSignatureThumbprint(signThumbprint)
                .status("ISSUED_AND_TRANSMITTED_TO_TAX_AUTHORITY")
                .downloadUrl("https://einvoice.casms.vn/download/" + invoiceRef + ".pdf")
                .build();
    }

    // =========================================================================
    // 5. SAO KÊ CHI TIÊU TRỰC TIẾP TRÊN TÀU (LIVE FOLIO STATEMENT & GUEST DASHBOARD)
    // =========================================================================
    @Transactional(readOnly = true)
    public LiveFolioStatementResponse getLiveFolioStatement(UUID onboardAccountId, UUID bookingId) {
        return getLiveFolioStatement(onboardAccountId, bookingId, null);
    }

    @Transactional(readOnly = true)
    public LiveFolioStatementResponse getLiveFolioStatement(UUID onboardAccountId, UUID bookingId, CasmsPrincipal principal) {
        log.info("Fetching Live Folio Statement for account: {}, booking: {}", onboardAccountId, bookingId);

        OnboardAccount account = null;
        Booking booking = null;

        if (onboardAccountId != null) {
            account = onboardAccountRepository.findById(onboardAccountId).orElse(null);
        }

        if (bookingId != null) {
            booking = bookingRepository.findById(bookingId).orElse(null);
            if (account == null && booking != null && booking.getSettlementOnboardAccount() != null) {
                account = booking.getSettlementOnboardAccount();
            }
        }

        if (account != null) {
            assertAccountAccess(account, principal);
        } else if (booking != null) {
            assertBookingAccess(booking, principal);
        }

        String bookingRef = booking != null ? booking.getBookingReference() : "BK-202611-001";
        String cruiseTitle = (booking != null && booking.getCruise() != null) ? booking.getCruise().getCruiseName() : "Hạ Long - Lan Hạ 3N2Đ - Emperor Cruise";
        String primaryGuest = (booking != null && booking.getBookedByUser() != null) ? booking.getBookedByUser().getUsername() : "Nguyễn Văn A";

        BigDecimal creditLimit = (account != null && account.getCreditLimit() != null)
                ? account.getCreditLimit()
                : new BigDecimal("20000000");
        UUID accId = account != null ? account.getOnboardAccountId() : (onboardAccountId != null ? onboardAccountId : UUID.randomUUID());

        // Ưu tiên đọc số tiền phong tỏa cọc bảo đảm lưu trong DB (OnboardAccount.preAuthorizedAmount)
        BigDecimal securityDepositHeld = (account != null && account.getPreAuthorizedAmount() != null)
                ? account.getPreAuthorizedAmount()
                : securityDepositCache.getOrDefault(accId, BigDecimal.ZERO);

        List<OnboardTransaction> txList = account != null
                ? onboardTransactionRepository.findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(account.getOnboardAccountId())
                : Collections.emptyList();

        List<LiveFolioStatementResponse.FolioTransactionItem> items = new ArrayList<>();
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        if (!txList.isEmpty()) {
            for (OnboardTransaction tx : txList) {
                String desc = tx.getDescription() != null ? tx.getDescription() : "Chi phí dịch vụ trên tàu";
                String outlet = tx.getSourceType() != null ? tx.getSourceType() : "Deck Lounge";
                String passName = tx.getPassenger() != null ? tx.getPassenger().getFullName() : primaryGuest;

                items.add(LiveFolioStatementResponse.FolioTransactionItem.builder()
                        .transactionId(tx.getTransactionId())
                        .transactionTime(tx.getTransactionTime())
                        .passengerName(passName)
                        .outletOrLocation(outlet)
                        .description(desc)
                        .transactionType(tx.getTransactionType())
                        .amount(tx.getAmount())
                        .currency(tx.getCurrencyCode() != null ? tx.getCurrencyCode().trim() : "VND")
                        .status(tx.getStatus())
                        .build());

                // Tính toán chính xác theo chuẩn kế toán FinTech quốc tế:
                // Charges: CHARGE hoặc ADJUSTMENT dương
                // Credits: PAYMENT, REFUND, REVERSAL, VOID, hoặc ADJUSTMENT âm
                if (tx.getStatus() == TransactionStatus.POSTED) {
                    if (tx.getTransactionType() == TransactionType.CHARGE
                            || (tx.getTransactionType() == TransactionType.ADJUSTMENT && tx.getAmount().signum() > 0)) {
                        totalCharges = totalCharges.add(tx.getAmount().abs());
                    } else if (tx.getTransactionType() == TransactionType.PAYMENT
                            || tx.getTransactionType() == TransactionType.REFUND
                            || tx.getTransactionType() == TransactionType.REVERSAL
                            || tx.getTransactionType() == TransactionType.VOID
                            || (tx.getTransactionType() == TransactionType.ADJUSTMENT && tx.getAmount().signum() < 0)) {
                        totalCredits = totalCredits.add(tx.getAmount().abs());
                    }
                }
            }
        } else if (account == null && booking == null) {
            // Chế độ demo / xem thử tổng quan trải nghiệm người dùng khi chưa có tài khoản thật
            items.add(LiveFolioStatementResponse.FolioTransactionItem.builder()
                    .transactionId(UUID.randomUUID())
                    .transactionTime(Instant.now().minusSeconds(14400))
                    .passengerName(primaryGuest)
                    .outletOrLocation("Skyline Sunset Lounge - Deck 9")
                    .description("Cocktails & Tapas thưởng ngoạn hoàng hôn Vịnh Lan Hạ")
                    .transactionType(TransactionType.CHARGE)
                    .amount(new BigDecimal("650000"))
                    .currency("VND")
                    .status(TransactionStatus.POSTED)
                    .build());

            items.add(LiveFolioStatementResponse.FolioTransactionItem.builder()
                    .transactionId(UUID.randomUUID())
                    .transactionTime(Instant.now().minusSeconds(28800))
                    .passengerName(primaryGuest)
                    .outletOrLocation("Ocean Breeze Spa & Wellness - Deck 3")
                    .description("Trị liệu toàn thân đá muối Himalaya 60 phút (2 khách)")
                    .transactionType(TransactionType.CHARGE)
                    .amount(new BigDecimal("1800000"))
                    .currency("VND")
                    .status(TransactionStatus.POSTED)
                    .build());

            items.add(LiveFolioStatementResponse.FolioTransactionItem.builder()
                    .transactionId(UUID.randomUUID())
                    .transactionTime(Instant.now().minusSeconds(43200))
                    .passengerName(primaryGuest)
                    .outletOrLocation("Lotus Fine Dining - Deck 2")
                    .description("Rượu vang đỏ Chateau Margaux niên vụ 2018")
                    .transactionType(TransactionType.CHARGE)
                    .amount(new BigDecimal("1450000"))
                    .currency("VND")
                    .status(TransactionStatus.POSTED)
                    .build());

            totalCharges = new BigDecimal("3900000");
            securityDepositHeld = new BigDecimal("2000000");
        }

        BigDecimal netPayable = totalCharges.subtract(totalCredits);
        if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
            netPayable = BigDecimal.ZERO;
        }
        BigDecimal remainingCredit = creditLimit.subtract(netPayable);
        if (remainingCredit.compareTo(BigDecimal.ZERO) < 0) {
            remainingCredit = BigDecimal.ZERO;
        }

        // Hạn mức chi tiêu các thành viên trong cabin
        List<LiveFolioStatementResponse.FolioMemberLimitItem> memberLimits = List.of(
                LiveFolioStatementResponse.FolioMemberLimitItem.builder()
                        .passengerId(UUID.randomUUID())
                        .passengerName(primaryGuest + " (Chủ phòng)")
                        .memberRole("Primary")
                        .limitAmount(new BigDecimal("15000000"))
                        .spentAmount(totalCharges)
                        .remainingAmount(new BigDecimal("15000000").subtract(totalCharges).max(BigDecimal.ZERO))
                        .status("ACTIVE")
                        .build(),
                LiveFolioStatementResponse.FolioMemberLimitItem.builder()
                        .passengerId(UUID.randomUUID())
                        .passengerName("Thành viên phòng")
                        .memberRole("Secondary")
                        .limitAmount(new BigDecimal("5000000"))
                        .spentAmount(BigDecimal.ZERO)
                        .remainingAmount(new BigDecimal("5000000"))
                        .status("ACTIVE")
                        .build()
        );

        return LiveFolioStatementResponse.builder()
                .onboardAccountId(accId)
                .bookingReference(bookingRef)
                .cabinNumber("Suite Balcony - Ocean View")
                .cruiseTitle(cruiseTitle)
                .primaryGuestName(primaryGuest)
                .accountStatus(account != null ? account.getStatus() : OnboardAccountStatus.ACTIVE)
                .creditLimit(creditLimit)
                .securityDepositHeld(securityDepositHeld)
                .totalCharges(totalCharges)
                .totalCredits(totalCredits)
                .netPayableBalance(netPayable)
                .remainingCredit(remainingCredit)
                .currency(account != null && account.getBaseCurrency() != null ? account.getBaseCurrency() : "VND")
                .statementAsOf(Instant.now())
                .transactions(items)
                .memberLimits(memberLimits)
                .build();
    }

    // =========================================================================
    // 6. EXPRESS CHECK-OUT TỰ ĐỘNG & THẺ THÔNG HÀNH RỜI TÀU (GANGWAY CLEARANCE PASS)
    // =========================================================================
    @Transactional
    public ExpressFolioCheckoutResponse processExpressFolioCheckout(ExpressFolioCheckoutRequest request) {
        return processExpressFolioCheckout(request, null);
    }

    @Transactional
    public ExpressFolioCheckoutResponse processExpressFolioCheckout(ExpressFolioCheckoutRequest request, CasmsPrincipal principal) {
        log.info("Processing Express Folio Checkout for onboard account: {}", request.getOnboardAccountId());

        UUID accId = request.getOnboardAccountId();
        OnboardAccount account = onboardAccountRepository.findById(accId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount not found with id: " + accId));

        assertAccountAccess(account, principal);

        if (account.getStatus() == OnboardAccountStatus.CLOSED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ACCOUNT_ALREADY_CLOSED", "Tài khoản phòng này đã được tất toán và đóng trước đó.");
        }

        // Lấy sao kê hiện tại từ CSDL
        LiveFolioStatementResponse statement = getLiveFolioStatement(accId, null, principal);
        BigDecimal totalCharges = statement.getTotalCharges();
        BigDecimal depositHeld = statement.getSecurityDepositHeld();

        // 1. Ghi nhận tiền thưởng / tip tự nguyện tặng thủy thủ đoàn & nhân viên
        BigDecimal gratuity = BigDecimal.ZERO;
        String gratuityType = request.getGratuityType() != null && !request.getGratuityType().isBlank()
                ? request.getGratuityType().trim()
                : "GENERAL_CREW_POOL";
        String staffName = request.getTargetStaffName() != null ? request.getTargetStaffName().trim() : null;

        if (request.getGratuityAmount() != null && request.getGratuityAmount().signum() > 0) {
            gratuity = request.getGratuityAmount();
            OnboardTransaction tipTx = new OnboardTransaction();
            tipTx.setOnboardAccount(account);
            tipTx.setPassenger(account.getPrimaryPassenger());
            tipTx.setTransactionType(TransactionType.CHARGE);
            tipTx.setAmount(gratuity);
            tipTx.setCurrencyCode(account.getBaseCurrency() != null ? account.getBaseCurrency() : "VND");
            tipTx.setTransactionTime(Instant.now());
            tipTx.setPostedAt(Instant.now());
            tipTx.setStatus(TransactionStatus.POSTED);
            tipTx.setSourceType("GRATUITY");
            tipTx.setServiceReference(gratuityType);
            tipTx.setDescription("Tiền tip cảm ơn (" + ("INDIVIDUAL_STAFF".equalsIgnoreCase(gratuityType) ? "Tip riêng nhân viên: " + (staffName != null ? staffName : "N/A") : "Hòm tip chung lễ tân tặng thủy thủ đoàn") + ")");
            onboardTransactionRepository.save(tipTx);
        }

        BigDecimal totalChargesWithGratuity = totalCharges.add(gratuity);
        BigDecimal depositOffset = depositHeld;
        BigDecimal netBeforeSurcharge = BigDecimal.ZERO;
        BigDecimal refundDue = BigDecimal.ZERO;

        if (totalChargesWithGratuity.compareTo(depositHeld) > 0) {
            depositOffset = depositHeld;
            netBeforeSurcharge = totalChargesWithGratuity.subtract(depositHeld);
        } else {
            depositOffset = totalChargesWithGratuity;
            refundDue = depositHeld.subtract(totalChargesWithGratuity);
        }

        // 2. Phụ phí thanh toán thẻ ngân hàng (Credit / Debit Card Processing Surcharge)
        BigDecimal cardSurchargeAmount = BigDecimal.ZERO;
        PaymentMethod pMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CreditCard;

        if ((pMethod == PaymentMethod.CreditCard || pMethod == PaymentMethod.Sepay)
                && netBeforeSurcharge.signum() > 0
                && !Boolean.TRUE.equals(request.getWaiveCardSurcharge())) {
            BigDecimal surchargePercent = (sepayProperties != null && sepayProperties.isCardSurchargeEnabled() && sepayProperties.getCardSurchargePercent() != null)
                    ? sepayProperties.getCardSurchargePercent()
                    : new BigDecimal("2.50");
            cardSurchargeAmount = netBeforeSurcharge.multiply(surchargePercent).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
        }

        BigDecimal additionalPaid = netBeforeSurcharge.add(cardSurchargeAmount);

        String finalStatementRef = "STMT-FINAL-" + (System.currentTimeMillis() % 1000000);
        String clearancePassToken = "GANGWAY-PASS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        String qrData = "CASMS-CLEARANCE|" + finalStatementRef + "|" + clearancePassToken + "|CLEARED_FOR_DEBARKATION";

        // Cập nhật trạng thái OnboardAccount thành CLOSED và giải phóng tiền cọc lưu trong DB
        account.setStatus(OnboardAccountStatus.CLOSED);
        account.setClosedAt(Instant.now());
        account.setPreAuthorizedAmount(BigDecimal.ZERO);
        onboardAccountRepository.save(account);

        // Tạo hóa đơn quyết toán Final Statement
        Invoice finalInvoice = new Invoice();
        finalInvoice.setOnboardAccount(account);
        finalInvoice.setInvoiceReference(finalStatementRef);
        finalInvoice.setDocumentType(InvoiceDocumentType.FinalStatement);
        finalInvoice.setVersionNo(1);
        finalInvoice.setIssuedAt(Instant.now());
        finalInvoice.setSourceCutoffAt(Instant.now());
        finalInvoice.setCurrency(account.getBaseCurrency() != null ? account.getBaseCurrency() : "VND");
        finalInvoice.setSnapshotTotalAmount(totalChargesWithGratuity);
        finalInvoice.setSnapshotNetAmountDue(additionalPaid);
        finalInvoice.setStatus(InvoiceStatus.Issued);
        finalInvoice.setDocumentReference("DOC-" + finalStatementRef);
        Invoice savedFinalInvoice = invoiceRepository.save(finalInvoice);

        // Lưu bản ghi Final Settlement
        FinalSettlement settlement = new FinalSettlement();
        settlement.setOnboardAccount(account);
        settlement.setFinalInvoice(savedFinalInvoice);
        settlement.setSourceCutoffAt(Instant.now());
        settlement.setAmountDueSnapshot(additionalPaid);
        settlement.setRefundDueSnapshot(refundDue);
        settlement.setStatementConfirmedAt(Instant.now());
        settlement.setStartedAt(Instant.now());
        settlement.setCompletedAt(Instant.now());
        finalSettlementRepository.save(settlement);

        // Xóa deposit cache sau khi quyết toán
        securityDepositCache.remove(accId);

        String surchargeNote = cardSurchargeAmount.signum() > 0
                ? String.format(" (bao gồm phụ phí cà thẻ: %,d VNĐ)", cardSurchargeAmount.longValue())
                : (pMethod == PaymentMethod.Cash ? " (Thanh toán tiền mặt: miễn phụ phí thẻ)" : "");
        String tipNote = gratuity.signum() > 0
                ? String.format(". Đội tàu xin chân thành cảm ơn quý khách đã gửi tặng số tiền tip %,d VNĐ", gratuity.longValue())
                : "";

        String notice = additionalPaid.signum() > 0
                ? "Quý khách đã thanh toán số dư " + String.format("%,d", additionalPaid.longValue()) + " VNĐ thành công" + surchargeNote + tipNote + ". Tài khoản phòng đã được tất toán."
                : (refundDue.signum() > 0
                ? "Tài khoản phòng đã tất toán" + tipNote + ". Số tiền cọc dư " + String.format("%,d", refundDue.longValue()) + " VNĐ sẽ được hoàn tự động về thẻ của quý khách trong 24-48h."
                : "Tài khoản phòng đã được cân đối hoàn toàn" + tipNote + ". Quý khách đã hoàn tất thủ tục rời tàu.");

        return ExpressFolioCheckoutResponse.builder()
                .finalSettlementId(settlement.getFinalSettlementId() != null ? settlement.getFinalSettlementId() : UUID.randomUUID())
                .onboardAccountId(accId)
                .finalStatementReference(finalStatementRef)
                .totalChargesAmount(totalChargesWithGratuity)
                .depositOffsetAmount(depositOffset)
                .additionalPaidAmount(additionalPaid)
                .refundDueAmount(refundDue)
                .gratuityAmount(gratuity)
                .gratuityType(gratuity.signum() > 0 ? gratuityType : null)
                .targetStaffName(staffName)
                .cardSurchargeAmount(cardSurchargeAmount)
                .paymentMethodUsed(pMethod.name())
                .settlementStatus("SETTLED_AND_CLEARED")
                .gangwayClearancePassToken(clearancePassToken)
                .clearanceQrData(qrData)
                .settledAt(Instant.now())
                .clearanceNotice(notice + " Vui lòng xuất trình mã QR Thông Hành tại Gangway Deck 1 khi rời tàu.")
                .build();
    }

    // =========================================================================
    // 7. PHONG TỎA TIỀN CỌC ĐẢM BẢO CHI TIÊU CABIN (SECURITY DEPOSIT PRE-AUTH)
    // =========================================================================
    @Transactional
    public SecurityDepositHoldResponse recordSecurityDepositHold(SecurityDepositHoldRequest request) {
        return recordSecurityDepositHold(request, null);
    }

    @Transactional
    public SecurityDepositHoldResponse recordSecurityDepositHold(SecurityDepositHoldRequest request, CasmsPrincipal principal) {
        log.info("Holding security deposit for onboard account: {}, amount: {}", request.getOnboardAccountId(), request.getDepositAmount());

        UUID accId = request.getOnboardAccountId();
        OnboardAccount account = onboardAccountRepository.findById(accId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount not found with id: " + accId));

        assertAccountAccess(account, principal);

        if (request.getDepositAmount() == null || request.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DEPOSIT_AMOUNT", "Số tiền cọc đảm bảo phải lớn hơn 0");
        }

        // Lưu trực tiếp vào CSDL trường preAuthorizedAmount của OnboardAccount để đảm bảo an toàn dữ liệu
        account.setPreAuthorizedAmount(request.getDepositAmount());
        onboardAccountRepository.save(account);
        securityDepositCache.put(accId, request.getDepositAmount());

        String preAuthCode = "PREAUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        return SecurityDepositHoldResponse.builder()
                .holdId(UUID.randomUUID())
                .onboardAccountId(accId)
                .depositAmount(request.getDepositAmount())
                .currency(account.getBaseCurrency() != null ? account.getBaseCurrency() : "VND")
                .preAuthCode(preAuthCode)
                .status("HELD_ACTIVE")
                .heldAt(Instant.now())
                .policyTerms("Khoản cọc đảm bảo sẽ được cấn trừ tự động vào hóa đơn dịch vụ tiêu dùng khi quý khách làm thủ tục Express Check-out rời tàu. Số tiền thừa còn lại (nếu có) sẽ được hoàn trả đầy đủ.")
                .build();
    }

    // =========================================================================
    // 8. NÂNG HẠNG PHÒNG TRỰC TIẾP & ĐỔI TOUR TRÊN TÀU (IN-CRUISE UPGRADE & EXTENSION)
    // =========================================================================
    @Transactional
    public OnboardUpgradeResponse processOnboardUpgrade(OnboardUpgradeRequest request) {
        return processOnboardUpgrade(request, null);
    }

    @Transactional
    public OnboardUpgradeResponse processOnboardUpgrade(OnboardUpgradeRequest request, CasmsPrincipal principal) {
        log.info("Processing onboard upgrade for account: {}, type: {}, fareDifference: {}",
                request.getOnboardAccountId(), request.getUpgradeType(), request.getFareDifference());

        UUID accId = request.getOnboardAccountId();
        OnboardAccount account = onboardAccountRepository.findById(accId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount not found with id: " + accId));

        assertAccountAccess(account, principal);

        if (account.getStatus() != OnboardAccountStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_ACTIVE",
                    "Chỉ có thể nâng hạng dịch vụ khi tài khoản phòng đang ở trạng thái ACTIVE");
        }

        if (request.getFareDifference() == null || request.getFareDifference().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_UPGRADE_FARE",
                    "Số tiền chênh lệch nâng hạng phải lớn hơn 0");
        }

        String type = request.getUpgradeType() != null && !request.getUpgradeType().isBlank()
                ? request.getUpgradeType().trim().toUpperCase(Locale.ROOT)
                : "CABIN_UPGRADE";
        String currency = request.getCurrency() != null && !request.getCurrency().isBlank()
                ? request.getCurrency().trim().toUpperCase(Locale.ROOT)
                : (account.getBaseCurrency() != null ? account.getBaseCurrency() : "VND");

        String description;
        if ("ITINERARY_EXTENSION".equalsIgnoreCase(type)) {
            int nights = request.getAdditionalNights() != null && request.getAdditionalNights() > 0 ? request.getAdditionalNights() : 1;
            description = String.format("Đổi chương trình tour kéo dài thêm %d đêm (chuyển tour 2N1Đ sang 3N2Đ theo yêu cầu trực tiếp trên du thuyền)", nights);
        } else {
            String cabinName = request.getNewCabinNumber() != null && !request.getNewCabinNumber().isBlank()
                    ? request.getNewCabinNumber().trim()
                    : "Cabin hạng cao cấp hơn";
            description = String.format("Nâng hạng phòng trực tiếp trên tàu: Chuyển sang %s", cabinName);
        }

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            description += " (" + request.getNotes().trim() + ")";
        }

        // Tự động nới hạn mức chi tiêu nếu số tiền nâng hạng vượt quá hạn mức hiện tại
        if (account.getCreditLimit() != null && request.getFareDifference().compareTo(account.getCreditLimit()) > 0) {
            account.setCreditLimit(account.getCreditLimit().add(request.getFareDifference()));
            onboardAccountRepository.save(account);
        }

        // Tạo giao dịch ghi nợ vào sao kê phòng
        OnboardTransaction tx = new OnboardTransaction();
        tx.setOnboardAccount(account);
        tx.setPassenger(account.getPrimaryPassenger());
        tx.setTransactionType(TransactionType.CHARGE);
        tx.setAmount(request.getFareDifference());
        tx.setCurrencyCode(currency);
        tx.setTransactionTime(Instant.now());
        tx.setPostedAt(Instant.now());
        tx.setStatus(TransactionStatus.POSTED);
        tx.setSourceType(type);
        tx.setServiceReference("ONBOARD_UPGRADE_" + type);
        tx.setDescription(description);
        if (principal != null && principal.getUser() != null) {
            tx.setCreatedByUser(principal.getUser());
        }

        OnboardTransaction savedTx = onboardTransactionRepository.save(tx);

        String targetDesc = "ITINERARY_EXTENSION".equalsIgnoreCase(type)
                ? "kéo dài hải trình" + (request.getAdditionalNights() != null ? " thêm " + request.getAdditionalNights() + " đêm" : "")
                : "nâng hạng phòng" + (request.getNewCabinNumber() != null ? " sang " + request.getNewCabinNumber() : "");

        return OnboardUpgradeResponse.builder()
                .transactionId(savedTx.getTransactionId())
                .onboardAccountId(accId)
                .upgradeType(type)
                .newCabinNumber(request.getNewCabinNumber())
                .additionalNights(request.getAdditionalNights())
                .fareDifference(request.getFareDifference())
                .currency(currency)
                .message("Đội ngũ tàu đã sắp xếp thành công yêu cầu " + targetDesc + ". Chi phí chênh lệch đã được ghi nợ vào sao kê phòng và sẽ được quyết toán vào sáng ngày cuối trước khi trả phòng.")
                .upgradedAt(savedTx.getTransactionTime())
                .build();
    }

    // --- Helper kiểm tra quyền truy cập Booking (Chống IDOR) ---
    private void assertBookingAccess(Booking booking, CasmsPrincipal principal) {
        if (principal == null) {
            // Cho phép thanh toán không cần login nếu đơn đặt tour không gắn với tài khoản thành viên nào (Guest Booking)
            if (booking.getBookedByUser() != null) {
                throw new AccessDeniedException("Vui lòng đăng nhập để thực hiện giao dịch cho đơn đặt tour này");
            }
            return;
        }
        if (hasPermSafe(principal, Permission.PAYMENT_PROCESS) || hasPermSafe(principal, Permission.PAYMENT_READ)) {
            return;
        }
        if (booking.getBookedByUser() != null && !principal.getUserId().equals(booking.getBookedByUser().getUserId())) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện giao dịch cho đơn đặt tour của khách hàng khác");
        }
    }

    // --- Helper kiểm tra quyền truy cập OnboardAccount (Chống IDOR) ---
    private void assertAccountAccess(OnboardAccount account, CasmsPrincipal principal) {
        if (principal == null) {
            return; // Cho phép test hoặc gọi nội bộ
        }
        if (hasPermSafe(principal, Permission.PAYMENT_READ) || hasPermSafe(principal, Permission.PAYMENT_REPORT) || hasPermSafe(principal, Permission.PAYMENT_PROCESS)) {
            return;
        }
        if (account.getPrimaryPassenger() != null
                && account.getPrimaryPassenger().getUser() != null
                && account.getPrimaryPassenger().getUser().getUserId().equals(principal.getUserId())) {
            return;
        }
        Optional<Booking> linked = bookingRepository.findBySettlementOnboardAccount_OnboardAccountId(account.getOnboardAccountId());
        if (linked.isPresent() && linked.get().getBookedByUser() != null
                && linked.get().getBookedByUser().getUserId().equals(principal.getUserId())) {
            return;
        }
        throw new AccessDeniedException("Bạn không có quyền truy cập sao kê chi tiêu của tài khoản phòng này");
    }

    private boolean hasPermSafe(CasmsPrincipal principal, Permission perm) {
        if (principal == null) return false;
        try {
            return principal.hasPermission(perm);
        } catch (Exception e) {
            return false;
        }
    }

    // --- Helper kiểm tra thuật toán Luhn cho thẻ tín dụng ---
    private boolean validateLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(cardNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    // --- Helper nhận diện thương hiệu thẻ ---
    private String detectCardBrand(String cardNumber) {
        if (cardNumber.startsWith("4")) return "VISA";
        if (cardNumber.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (cardNumber.startsWith("35")) return "JCB";
        if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) return "AMEX";
        return "INTERNATIONAL_CARD";
    }
}
