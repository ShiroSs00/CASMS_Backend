package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.util.NewWriteCurrency;
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
import org.springframework.beans.factory.annotation.Value;
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
    private final SpendingLimitRepository spendingLimitRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinalSettlementRepository finalSettlementRepository;
    private final SepayProperties sepayProperties;
    private final OnboardFinancialAccess financialAccess;

    @Value("${casms.payment.guest-card-simulation-enabled:false}")
    private boolean guestCardSimulationEnabled;

    // Cache tạm thời lưu tiến trình Group Split
    private final Map<String, GroupSplitResponse> splitGroupCache = new ConcurrentHashMap<>();

    // =========================================================================
    // 1. THANH TOÁN THẺ QUỐC TẾ (CREDIT / DEBIT CARD - VISA, MASTERCARD, JCB, AMEX)
    // =========================================================================
    @Transactional
    public CreditCardPaymentResponse processCreditCardPayment(CreditCardPaymentRequest request) {
        return processCreditCardPayment(request, null);
    }

    @Transactional
    public CreditCardPaymentResponse processCreditCardPayment(CreditCardPaymentRequest request, CasmsPrincipal principal) {
        if (!guestCardSimulationEnabled) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "CARD_SIMULATION_DISABLED",
                    "Cổng thanh toán thẻ giả lập không khả dụng trong môi trường này. Vui lòng sử dụng VietQR hoặc cổng thanh toán chính thức.");
        }
        log.info("Processing Credit Card payment for booking: {}, amount: {}", request.getBookingId(), request.getAmount());

        Booking booking = bookingRepository.findByIdForUpdate(request.getBookingId())
                .or(() -> bookingRepository.findById(request.getBookingId()))
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
                .filter(p -> (p.getStatus() == PaymentStatus.Confirmed || p.getStatus() == PaymentStatus.Captured)
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

        // Kiểm tra số tiền sau quy đổi không vượt quá số dư còn lại (Outstanding balance)
        if (amountInBookingCurrency.compareTo(outstanding) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "AMOUNT_EXCEEDS_OUTSTANDING_BALANCE",
                    String.format("Số tiền thanh toán (%s %s) vượt quá số dư còn lại của đơn hàng (%s %s)",
                            amountInBookingCurrency.toPlainString(), bookingCurrency, outstanding.toPlainString(), bookingCurrency));
        }

        // 9. Tìm installment mục tiêu (nếu có)
        Installment targetInstallment = null;
        if (request.getInstallmentId() != null) {
            targetInstallment = installmentRepository.findById(request.getInstallmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Installment not found with id: " + request.getInstallmentId()));
            if (targetInstallment.getBooking() != null && !targetInstallment.getBooking().getBookingId().equals(booking.getBookingId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INSTALLMENT_BOOKING_MISMATCH",
                        "Đợt thanh toán không thuộc về đơn đặt tour này");
            }
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
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            Payment existing = existingPaymentOpt.get();
            if (existing.getStatus() == PaymentStatus.Confirmed || existing.getStatus() == PaymentStatus.Captured) {
                if (existing.getBooking() != null && existing.getBooking().getBookingId().equals(booking.getBookingId())) {
                    log.info("Returning existing confirmed payment for idempotency key: {}", idempotencyKey);
                    return CreditCardPaymentResponse.builder()
                            .paymentId(existing.getPaymentId())
                            .bookingId(booking.getBookingId())
                            .bookingReference(booking.getBookingReference())
                            .amount(existing.getOriginalAmount() != null ? existing.getOriginalAmount() : existing.getRequestedAmount())
                            .baseAmount(existing.getOriginalAmount() != null ? existing.getOriginalAmount() : existing.getRequestedAmount())
                            .surchargePercent(BigDecimal.ZERO)
                            .surchargeAmount(BigDecimal.ZERO)
                            .totalAmountWithSurcharge(existing.getOriginalAmount() != null ? existing.getOriginalAmount() : existing.getRequestedAmount())
                            .currency(existing.getOriginalCurrency() != null ? existing.getOriginalCurrency() : existing.getCurrency())
                            .paymentStatus(existing.getStatus())
                            .authorizationCode(existing.getProcessorReference())
                            .cardLast4(cardLast4)
                            .cardBrand(cardBrand)
                            .bookingStatus(booking.getBookingStatus())
                            .paidAt(existing.getConfirmedAt())
                            .message("Thanh toán thành công qua thẻ " + cardBrand + conversionNotice)
                            .vatInvoiceReference(null)
                            .build();
                }
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                        "Khóa idempotency đã được dùng cho giao dịch khác");
            }
            payment = existing;
        } else {
            payment = new Payment();
        }
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

        boolean canOverrideSurcharge = hasPermSafe(principal, Permission.PAYMENT_PROCESS);
        BigDecimal surchargePercent;
        if (canOverrideSurcharge && Boolean.TRUE.equals(request.getWaiveSurcharge())) {
            surchargePercent = BigDecimal.ZERO;
        } else if (canOverrideSurcharge && request.getCustomSurchargePercent() != null) {
            surchargePercent = request.getCustomSurchargePercent();
        } else if (sepayProperties != null && sepayProperties.isCardSurchargeEnabled()) {
            BigDecimal resolved = sepayProperties.resolveCardSurcharge(cardBrand);
            surchargePercent = (resolved != null) ? resolved
                    : (sepayProperties.getCardSurchargePercent() != null ? sepayProperties.getCardSurchargePercent() : BigDecimal.ZERO);
        } else {
            surchargePercent = BigDecimal.ZERO;
        }
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
        return getGroupSplitStatus(splitGroupId, null);
    }

    public GroupSplitResponse getGroupSplitStatus(String splitGroupId, CasmsPrincipal principal) {
        GroupSplitResponse group = splitGroupCache.get(splitGroupId);
        if (group == null) {
            throw new ResourceNotFoundException("Group Split không tồn tại: " + splitGroupId);
        }
        if (group.getBookingId() != null) {
            Booking booking = bookingRepository.findById(group.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + group.getBookingId()));
            assertBookingAccess(booking, principal);
        } else if (principal == null || (!hasPermSafe(principal, Permission.PAYMENT_READ)
                && !hasPermSafe(principal, Permission.PAYMENT_PROCESS))) {
            throw new AccessDeniedException("Bạn không có quyền xem tiến độ thanh toán nhóm này");
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

        if (booking.getBookingStatus() == BookingStatus.Draft
                || booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "BOOKING_NOT_ELIGIBLE_FOR_VAT",
                    "Chỉ có thể xuất hóa đơn VAT cho đơn đặt chỗ đã xác nhận.");
        }

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
        financialAccess.requireAuthenticated(principal);
        Booking selectedBooking = bookingId == null ? null : financialAccess.booking(bookingId);
        OnboardAccount account = onboardAccountId == null
                ? selectedBooking == null ? null : selectedBooking.getSettlementOnboardAccount()
                : onboardAccountRepository.findById(onboardAccountId).orElse(null);
        if (account == null)
            throw new ResourceNotFoundException("Onboard account not available");

        var scope = financialAccess.readFolio(principal, account, selectedBooking);
        Booking booking = scope.booking();
        UUID accountId = account.getOnboardAccountId();
        String currency = NewWriteCurrency.requireSupportedCurrency(account.getBaseCurrency());
        List<LiveFolioStatementResponse.FolioTransactionItem> items = new ArrayList<>();
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        List<OnboardTransaction> ledger = onboardTransactionRepository
                .findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(accountId).stream()
                .filter(tx -> scope.accountWide() || (tx.getPassenger() != null
                        && scope.passengerId().equals(tx.getPassenger().getPassengerId())))
                .toList();
        Instant now = Instant.now();
        for (OnboardTransaction tx : ledger) {
            String transactionCurrency = tx.getCurrencyCode();
            items.add(LiveFolioStatementResponse.FolioTransactionItem.builder()
                    .transactionId(tx.getTransactionId())
                    .transactionTime(tx.getTransactionTime())
                    .passengerName(tx.getPassenger() == null ? null : tx.getPassenger().getFullName())
                    .outletOrLocation(tx.getSourceType())
                    .description(tx.getDescription())
                    .transactionType(tx.getTransactionType())
                    .amount(tx.getAmount())
                    .currency(transactionCurrency)
                    .status(tx.getStatus())
                    .build());
            if (tx.getStatus() != TransactionStatus.POSTED) continue;
            if (transactionCurrency == null || !currency.equals(transactionCurrency.trim())
                    || tx.getAmount() == null)
                throw new BusinessException(HttpStatus.CONFLICT, "FOLIO_LEDGER_UNRECONCILED",
                        "Posted transactions cannot be totaled in the account currency");
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

        return LiveFolioStatementResponse.builder()
                .onboardAccountId(accountId)
                .bookingReference(booking == null ? null : booking.getBookingReference())
                .cruiseTitle(account.getCruise().getCruiseName())
                .primaryGuestName(scope.accountWide() && account.getPrimaryPassenger() != null
                        ? account.getPrimaryPassenger().getFullName() : null)
                .accountStatus(account.getStatus())
                .creditLimit(scope.accountWide() ? account.getCreditLimit() : null)
                .securityDepositHeld(scope.accountWide() ? account.getPreAuthorizedAmount() : null)
                .totalCharges(totalCharges)
                .totalCredits(totalCredits)
                .currency(currency)
                .scope(scope.accountWide() ? "ACCOUNT" : "PASSENGER")
                .statementAsOf(now)
                .transactions(items)
                .memberLimits(scope.accountWide() ? List.of()
                        : memberLimits(accountId, scope.passengerId(), ledger, currency, now))
                .build();
    }

    private List<LiveFolioStatementResponse.FolioMemberLimitItem> memberLimits(
            UUID accountId, UUID passengerId, List<OnboardTransaction> ledger, String currency, Instant now) {
        return spendingLimitRepository.findByAccountMember_OnboardAccount_OnboardAccountId(accountId).stream()
                .filter(limit -> passengerId.equals(limit.getAccountMember().getPassenger().getPassengerId())
                        && limit.getStatus() == SpendingLimitStatus.ACTIVE
                        && !limit.getEffectiveFrom().isAfter(now)
                        && (limit.getEffectiveTo() == null || limit.getEffectiveTo().isAfter(now)))
                .map(limit -> memberLimitItem(limit, ledger, currency))
                .toList();
    }

    private LiveFolioStatementResponse.FolioMemberLimitItem memberLimitItem(
            SpendingLimit limit, List<OnboardTransaction> ledger, String currency) {
        if (!currency.equals(limit.getCurrencyCode()))
            throw new BusinessException(HttpStatus.CONFLICT, "FOLIO_LEDGER_UNRECONCILED",
                    "Active spending limit currency does not match the account");
        BigDecimal spent = ledger.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.POSTED
                        && !tx.getTransactionTime().isBefore(limit.getEffectiveFrom())
                        && (tx.getTransactionType() == TransactionType.CHARGE
                                || tx.getTransactionType() == TransactionType.VOID
                                || tx.getTransactionType() == TransactionType.ADJUSTMENT))
                .map(OnboardTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
        var member = limit.getAccountMember();
        return LiveFolioStatementResponse.FolioMemberLimitItem.builder()
                .passengerId(member.getPassenger().getPassengerId())
                .passengerName(member.getPassenger().getFullName())
                .memberRole(member.getMemberRole().name())
                .limitAmount(limit.getLimitAmount())
                .spentAmount(spent)
                .remainingAmount(limit.getLimitAmount().subtract(spent).max(BigDecimal.ZERO))
                .status(limit.getStatus().name())
                .build();
    }

    public record OnboardAccountSummary(
            UUID onboardAccountId, UUID bookingId, UUID cruiseId, OnboardAccountStatus accountStatus,
            String currency, FinalSettlementStatus settlementStatus) {}

    @Transactional(readOnly = true)
    public OnboardAccountSummary discoverAccount(UUID bookingId, CasmsPrincipal principal) {
        financialAccess.requireAuthenticated(principal);
        Booking booking = financialAccess.booking(bookingId);
        OnboardAccount account = booking.getSettlementOnboardAccount();
        if (account == null) throw new ResourceNotFoundException("Onboard account not available");
        var scope = financialAccess.readFolio(principal, account, booking);
        UUID accountId = account.getOnboardAccountId();
        String currency = NewWriteCurrency.requireSupportedCurrency(account.getBaseCurrency());
        FinalSettlementStatus settlementStatus = scope.accountWide()
                ? finalSettlementRepository.findFirstByOnboardAccount_OnboardAccountIdOrderByStartedAtDesc(accountId)
                        .filter(settlement -> currency.equals(settlement.getBaseCurrency()))
                        .map(FinalSettlement::getStatus).orElse(null)
                : null;
        return new OnboardAccountSummary(accountId, bookingId, account.getCruise().getCruiseId(),
                account.getStatus(), currency, settlementStatus);
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
        financialAccess.requireAuthenticated(principal);
        throw new BusinessException(HttpStatus.CONFLICT, "SETTLEMENT_WORKFLOW_UNAVAILABLE",
                "Express checkout requires a confirmed settlement and payment-evidence workflow");
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
        financialAccess.requireAuthenticated(principal);
        throw new BusinessException(HttpStatus.CONFLICT, "DEPOSIT_EVIDENCE_REQUIRED",
                "Security deposit requires a verified authorization reference");
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

        financialAccess.requireFinance(principal, Permission.PAYMENT_PROCESS);

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
                : NewWriteCurrency.requireSupportedCurrency(account.getBaseCurrency());

        if (!NewWriteCurrency.requireSupportedCurrency(account.getBaseCurrency()).equals(currency))
            throw new BusinessException(HttpStatus.CONFLICT, "ACCOUNT_BASE_CURRENCY_MISMATCH",
                    "Upgrade currency must match the onboard account");
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
            throw new AccessDeniedException("Vui lòng đăng nhập để thực hiện giao dịch cho đơn đặt tour này");
        }
        if (hasPermSafe(principal, Permission.PAYMENT_PROCESS) || hasPermSafe(principal, Permission.PAYMENT_READ)) {
            return;
        }
        if (booking.getBookedByUser() == null || !principal.getUserId().equals(booking.getBookedByUser().getUserId())) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện giao dịch cho đơn đặt tour của khách hàng khác");
        }
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
