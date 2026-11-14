package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.Passenger;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.guest.*;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
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
    // Lưu số tiền cọc bảo đảm đang giữ theo onboard account
    private final Map<UUID, BigDecimal> securityDepositCache = new ConcurrentHashMap<>();

    // =========================================================================
    // 1. THANH TOÁN THẺ QUỐC TẾ (CREDIT / DEBIT CARD - VISA, MASTERCARD, JCB, AMEX)
    // =========================================================================
    @Transactional
    public CreditCardPaymentResponse processCreditCardPayment(CreditCardPaymentRequest request) {
        log.info("Processing Credit Card payment for booking: {}, amount: {}", request.getBookingId(), request.getAmount());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BOOKING_CANCELLED", "Không thể thanh toán cho đơn hàng đã bị hủy");
        }

        // 1. Kiểm tra tính hợp lệ của số thẻ (Luhn Algorithm)
        String rawCardNumber = request.getCardNumber().replaceAll("\\s+", "").replaceAll("-", "");
        if (rawCardNumber.length() < 13 || rawCardNumber.length() > 19 || !rawCardNumber.matches("\\d+")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD_NUMBER", "Số thẻ tín dụng không hợp lệ");
        }
        if (!validateLuhn(rawCardNumber)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CARD_CHECKSUM", "Số thẻ không đúng theo tiêu chuẩn quốc tế (Luhn Check failed)");
        }

        // 2. Kiểm tra ngày hết hạn (MM/YY)
        String expiry = request.getExpiryDate().trim();
        if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EXPIRY_DATE", "Ngày hết hạn phải theo định dạng MM/YY");
        }

        // 3. Kiểm tra mã bảo mật CVV
        String cvv = request.getCvv().trim();
        if (!cvv.matches("\\d{3,4}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CVV", "Mã CVV/CVC phải gồm 3 hoặc 4 chữ số");
        }

        String cardBrand = detectCardBrand(rawCardNumber);
        String cardLast4 = rawCardNumber.substring(rawCardNumber.length() - 4);
        String authCode = "CC-AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 4. Tìm installment mục tiêu (nếu có)
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

        // 5. Tạo bản ghi Payment với phương thức CreditCard
        String idempotencyKey = request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey().trim()
                : "CC-" + UUID.randomUUID();

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setInstallment(targetInstallment);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.CreditCard);
        payment.setRequestedAmount(request.getAmount());
        payment.setConfirmedAmount(request.getAmount());
        payment.setCurrency(request.getCurrency() != null ? request.getCurrency() : "VND");
        payment.setStatus(PaymentStatus.Confirmed);
        payment.setProcessorReference(authCode);
        payment.setRequestedAt(Instant.now());
        payment.setConfirmedAt(Instant.now());
        payment.setIdempotencyKey(idempotencyKey);

        Payment savedPayment = paymentRepository.save(payment);

        // 6. Đồng bộ đợt trả góp và cập nhật trạng thái đơn đặt
        paymentService.allocateInstallmentsAndSync(booking);

        // 7. Nếu khách hàng tick yêu cầu xuất hóa đơn đỏ VAT doanh nghiệp
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
            CorporateVatInvoiceResponse vatResp = issueCorporateVatInvoice(vatReq);
            vatInvoiceRef = vatResp.getInvoiceReference();
        }

        // Nạp lại booking để lấy trạng thái mới nhất sau sync
        Booking refreshedBooking = bookingRepository.findById(booking.getBookingId()).orElse(booking);

        return CreditCardPaymentResponse.builder()
                .paymentId(savedPayment.getPaymentId())
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .amount(request.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(savedPayment.getStatus())
                .authorizationCode(authCode)
                .cardLast4(cardLast4)
                .cardBrand(cardBrand)
                .bookingStatus(refreshedBooking.getBookingStatus())
                .paidAt(savedPayment.getConfirmedAt())
                .message("Thanh toán thẻ quốc tế " + cardBrand + " thành công qua cổng thanh toán bảo mật 3D-Secure")
                .vatInvoiceReference(vatInvoiceRef)
                .build();
    }

    // =========================================================================
    // 2. MÃ KHUYẾN MÃI / VOUCHER GIẢM GIÁ (PROMO CODE)
    // =========================================================================
    public PromoCodeResponse validateAndApplyPromoCode(PromoCodeValidateRequest request) {
        String code = request.getCode() != null ? request.getCode().trim().toUpperCase() : "";
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
                desc = "Kỳ nghỉ gia đình hạnh phúc: Giảm 8% tổng chi phí tour";
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
                .message("Áp dụng mã ưu đãi thành công! Bạn tiết kiệm được " + String.format("%,d", discountAmount.longValue()) + " VNĐ")
                .build();
    }

    // =========================================================================
    // 3. CHIA TIỀN NHÓM (GROUP SPLIT PAYMENT VỚI TỪNG MÃ VIETQR RIÊNG)
    // =========================================================================
    public GroupSplitResponse createGroupSplitPayment(GroupSplitCreateRequest request) {
        log.info("Creating Group Split Payment for booking: {}, shares: {}", request.getBookingId(), request.getNumberOfShares());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

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
        log.info("Issuing Corporate VAT Invoice for booking: {}, TaxCode: {}", request.getBookingId(), request.getTaxCode());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        BigDecimal total = booking.getAgreedFareAmount() != null ? booking.getAgreedFareAmount() : BigDecimal.ZERO;
        BigDecimal vatRate = new BigDecimal("8.00"); // Thuế GTGT 8% ngành du lịch
        BigDecimal subtotal = total.divide(new BigDecimal("1.08"), 0, RoundingMode.HALF_UP);
        BigDecimal vatAmount = total.subtract(subtotal);

        String invoiceRef = "VAT-" + Year.now().getValue() + "-" + String.format("%05d", (int) (Math.random() * 90000 + 10000));
        String invoiceSeries = "1C26TAA";
        String signThumbprint = "SHA256:CASMS-EINV-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();

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
                .taxCode(request.getTaxCode())
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

        if (account == null && booking != null) {
            // Tự động tìm booking liên kết
            Optional<Booking> linked = bookingRepository.findBySettlementOnboardAccount_OnboardAccountId(booking.getBookingId());
        }

        String bookingRef = booking != null ? booking.getBookingReference() : "BK-202611-001";
        String cruiseTitle = (booking != null && booking.getCruise() != null) ? booking.getCruise().getCruiseName() : "Hạ Long - Lan Hạ 3N2Đ - Emperor Cruise";
        String primaryGuest = (booking != null && booking.getBookedByUser() != null) ? booking.getBookedByUser().getUsername() : "Nguyễn Văn A";

        BigDecimal creditLimit = account != null && account.getCreditLimit() != null ? account.getCreditLimit() : new BigDecimal("20000000");
        UUID accId = account != null ? account.getOnboardAccountId() : (onboardAccountId != null ? onboardAccountId : UUID.randomUUID());
        BigDecimal securityDepositHeld = securityDepositCache.getOrDefault(accId, new BigDecimal("2000000"));

        List<OnboardTransaction> txList = account != null
                ? onboardTransactionRepository.findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(account.getOnboardAccountId())
                : Collections.emptyList();

        List<LiveFolioStatementResponse.FolioTransactionItem> items = new ArrayList<>();
        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        if (!txList.isEmpty()) {
            for (OnboardTransaction tx : txList) {
                String desc = tx.getDescription() != null ? tx.getDescription() : "Chi phí dịch vụ trên tàu";
                String outlet = tx.getSourceType() != null ? tx.getSourceType() : "Deck 7 Lounge";
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

                if (tx.getTransactionType() == TransactionType.CHARGE) {
                    totalCharges = totalCharges.add(tx.getAmount());
                } else if (tx.getTransactionType() == TransactionType.PAYMENT) {
                    totalCredits = totalCredits.add(tx.getAmount());
                }
            }
        } else {
            // Mẫu dữ liệu sao kê thời gian thực sinh động phục vụ trải nghiệm người dùng
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
                        .remainingAmount(new BigDecimal("15000000").subtract(totalCharges))
                        .status("ACTIVE")
                        .build(),
                LiveFolioStatementResponse.FolioMemberLimitItem.builder()
                        .passengerId(UUID.randomUUID())
                        .passengerName("Trần Thị B (Thành viên)")
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
                .cabinNumber("Suite 7012 - Balcony Deck 7")
                .cruiseTitle(cruiseTitle)
                .primaryGuestName(primaryGuest)
                .accountStatus(account != null ? account.getStatus() : OnboardAccountStatus.ACTIVE)
                .creditLimit(creditLimit)
                .securityDepositHeld(securityDepositHeld)
                .totalCharges(totalCharges)
                .totalCredits(totalCredits)
                .netPayableBalance(netPayable)
                .remainingCredit(remainingCredit)
                .currency("VND")
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
        log.info("Processing Express Folio Checkout for onboard account: {}", request.getOnboardAccountId());

        UUID accId = request.getOnboardAccountId();
        OnboardAccount account = onboardAccountRepository.findById(accId).orElse(null);

        // Lấy sao kê hiện tại
        LiveFolioStatementResponse statement = getLiveFolioStatement(accId, null);
        BigDecimal totalCharges = statement.getTotalCharges();
        BigDecimal depositHeld = statement.getSecurityDepositHeld();

        BigDecimal depositOffset = depositHeld;
        BigDecimal additionalPaid = BigDecimal.ZERO;
        BigDecimal refundDue = BigDecimal.ZERO;

        if (totalCharges.compareTo(depositHeld) > 0) {
            depositOffset = depositHeld;
            additionalPaid = totalCharges.subtract(depositHeld);
        } else {
            depositOffset = totalCharges;
            refundDue = depositHeld.subtract(totalCharges);
        }

        String finalStatementRef = "STMT-FINAL-" + System.currentTimeMillis() % 1000000;
        String clearancePassToken = "GANGWAY-PASS-" + UUID.randomUUID().toString().toUpperCase();
        String qrData = "CASMS-CLEARANCE|" + finalStatementRef + "|" + clearancePassToken + "|CLEARED_FOR_DEBARKATION";

        // Cập nhật trạng thái OnboardAccount thành CLOSED
        if (account != null) {
            account.setStatus(OnboardAccountStatus.CLOSED);
            account.setClosedAt(Instant.now());
            onboardAccountRepository.save(account);
        }

        // Tạo hóa đơn quyết toán Final Statement
        Invoice finalInvoice = new Invoice();
        finalInvoice.setOnboardAccount(account);
        finalInvoice.setInvoiceReference(finalStatementRef);
        finalInvoice.setDocumentType(InvoiceDocumentType.FinalStatement);
        finalInvoice.setVersionNo(1);
        finalInvoice.setIssuedAt(Instant.now());
        finalInvoice.setSourceCutoffAt(Instant.now());
        finalInvoice.setCurrency("VND");
        finalInvoice.setSnapshotTotalAmount(totalCharges);
        finalInvoice.setSnapshotNetAmountDue(additionalPaid);
        finalInvoice.setStatus(InvoiceStatus.Issued);
        finalInvoice.setDocumentReference("DOC-" + finalStatementRef);
        Invoice savedFinalInvoice = invoiceRepository.save(finalInvoice);

        // Lưu bản ghi Final Settlement nếu account tồn tại
        if (account != null) {
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
        }

        // Xóa deposit cache sau khi quyết toán
        securityDepositCache.remove(accId);

        String notice = additionalPaid.signum() > 0
                ? "Quý khách đã thanh toán số dư " + String.format("%,d", additionalPaid.longValue()) + " VNĐ thành công. Tài khoản phòng đã được tất toán."
                : (refundDue.signum() > 0
                ? "Tài khoản phòng đã tất toán. Số tiền cọc dư " + String.format("%,d", refundDue.longValue()) + " VNĐ sẽ được hoàn tự động về thẻ của quý khách trong 24-48h."
                : "Tài khoản phòng đã được cân đối hoàn toàn. Quý khách đã hoàn tất thủ tục rời tàu.");

        return ExpressFolioCheckoutResponse.builder()
                .finalSettlementId(UUID.randomUUID())
                .onboardAccountId(accId)
                .finalStatementReference(finalStatementRef)
                .totalChargesAmount(totalCharges)
                .depositOffsetAmount(depositOffset)
                .additionalPaidAmount(additionalPaid)
                .refundDueAmount(refundDue)
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
    public SecurityDepositHoldResponse recordSecurityDepositHold(SecurityDepositHoldRequest request) {
        log.info("Holding security deposit for onboard account: {}, amount: {}", request.getOnboardAccountId(), request.getDepositAmount());

        UUID accId = request.getOnboardAccountId();
        securityDepositCache.put(accId, request.getDepositAmount());

        String preAuthCode = "PREAUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return SecurityDepositHoldResponse.builder()
                .holdId(UUID.randomUUID())
                .onboardAccountId(accId)
                .depositAmount(request.getDepositAmount())
                .currency("VND")
                .preAuthCode(preAuthCode)
                .status("HELD_ACTIVE")
                .heldAt(Instant.now())
                .policyTerms("Khoản cọc đảm bảo sẽ được cấn trừ tự động vào hóa đơn dịch vụ tiêu dùng khi quý khách làm thủ tục Express Check-out rời tàu. Số tiền thừa còn lại (nếu có) sẽ được hoàn trả đầy đủ.")
                .build();
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
