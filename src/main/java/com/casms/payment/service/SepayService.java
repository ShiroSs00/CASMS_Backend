package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.SepayCheckoutRequest;
import com.casms.payment.dto.request.SepayQuickCheckoutRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.BookingPaymentOptionsResponse;
import com.casms.payment.dto.response.PaymentStatusCheckResponse;
import com.casms.payment.dto.response.SepayCheckoutResponse;
import com.casms.payment.dto.response.SepayWebhookResponse;
import com.casms.payment.entity.Installment;
import com.casms.payment.entity.Payment;
import com.casms.payment.entity.SepayTransaction;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.PaymentType;
import com.casms.payment.enums.SepayTransactionStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.SepayTransactionRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SepayService {

    private final SepayProperties sepayProperties;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final InstallmentRepository installmentRepository;
    private final SepayTransactionRepository sepayTransactionRepository;
    private final PaymentService paymentService;
    private final BookingService bookingService;

    private static final DateTimeFormatter SEPAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lấy chi tiết 3 hình thức thanh toán (FULL, DEPOSIT, STAGE) của một đơn đặt tour.
     */
    @Transactional(readOnly = true)
    public BookingPaymentOptionsResponse getPaymentOptions(UUID bookingId, UUID userId, boolean canReadAll) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        assertAccess(booking, userId, canReadAll);

        BigDecimal agreedFare = booking.getAgreedFareAmount() != null ? booking.getAgreedFareAmount() : BigDecimal.ZERO;
        BigDecimal exchangeRate = sepayProperties.getUsdToVndRate();

        BigDecimal totalPaid = paymentRepository.findForBooking(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getConfirmedAmount() != null)
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = agreedFare.subtract(totalPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
            outstanding = BigDecimal.ZERO;
        }

        List<Installment> installments = installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId);

        // 1. Tuỳ chọn FULL: Thanh toán toàn bộ số tiền còn lại
        boolean fullAvailable = outstanding.compareTo(BigDecimal.ZERO) > 0
                && booking.getBookingStatus() != BookingStatus.Cancelled;
        BigDecimal fullVnd = outstanding.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
        BookingPaymentOptionsResponse.FullPaymentOption fullOption = BookingPaymentOptionsResponse.FullPaymentOption.builder()
                .isAvailable(fullAvailable)
                .amountUsd(outstanding)
                .amountVnd(fullVnd)
                .description("Thanh toán toàn bộ 100% số tiền còn lại (" + outstanding + " " + booking.getCurrency() + ")")
                .build();

        // 2. Tuỳ chọn DEPOSIT: Đặt cọc (Đợt 1)
        BookingPaymentOptionsResponse.DepositPaymentOption depositOption = null;
        if (!installments.isEmpty()) {
            Installment inst1 = installments.get(0);
            BigDecimal paidForInst1 = getPaidAmountForInstallment(bookingId, inst1.getInstallmentId());
            BigDecimal remInst1 = inst1.getRequiredAmount().subtract(paidForInst1);
            if (remInst1.compareTo(BigDecimal.ZERO) < 0) {
                remInst1 = BigDecimal.ZERO;
            }
            boolean isDepositPaid = remInst1.compareTo(BigDecimal.ZERO) <= 0;
            boolean isDepositAvail = !isDepositPaid && booking.getBookingStatus() != BookingStatus.Cancelled;

            depositOption = BookingPaymentOptionsResponse.DepositPaymentOption.builder()
                    .isAvailable(isDepositAvail)
                    .isPaid(isDepositPaid)
                    .installmentId(inst1.getInstallmentId())
                    .sequenceNo(inst1.getSequenceNo())
                    .requiredAmountUsd(inst1.getRequiredAmount())
                    .requiredAmountVnd(inst1.getRequiredAmount().multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP))
                    .paidAmountUsd(paidForInst1)
                    .remainingAmountUsd(remInst1)
                    .remainingAmountVnd(remInst1.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP))
                    .dueAt(inst1.getDueAt())
                    .description("Đặt cọc giữ chỗ (" + inst1.getRequiredAmount() + " " + booking.getCurrency() + ")")
                    .build();
        }

        // 3. Tuỳ chọn STAGE: Thanh toán theo từng giai đoạn
        List<BookingPaymentOptionsResponse.StageDetail> stageDetails = new ArrayList<>();
        boolean foundCurrentDue = false;
        for (Installment inst : installments) {
            BigDecimal paidForInst = getPaidAmountForInstallment(bookingId, inst.getInstallmentId());
            BigDecimal remInst = inst.getRequiredAmount().subtract(paidForInst);
            if (remInst.compareTo(BigDecimal.ZERO) < 0) {
                remInst = BigDecimal.ZERO;
            }
            boolean isPaid = remInst.compareTo(BigDecimal.ZERO) <= 0;
            boolean isCurrentDue = false;
            if (!isPaid && !foundCurrentDue) {
                isCurrentDue = true;
                foundCurrentDue = true;
            }

            String desc;
            if (installments.size() == 1) {
                desc = "Đợt duy nhất: 100% tiền vé";
            } else if (installments.size() == 2) {
                desc = inst.getSequenceNo() == 1 ? "Đợt 1: Đặt cọc 30%" : "Đợt 2: Thanh toán 70% còn lại";
            } else {
                desc = switch (inst.getSequenceNo()) {
                    case 1 -> "Đợt 1: Đặt cọc 30%";
                    case 2 -> "Đợt 2: Thanh toán giữa kỳ 40%";
                    default -> "Đợt " + inst.getSequenceNo() + ": Thanh toán 30% còn lại";
                };
            }

            stageDetails.add(BookingPaymentOptionsResponse.StageDetail.builder()
                    .installmentId(inst.getInstallmentId())
                    .sequenceNo(inst.getSequenceNo())
                    .installmentType(inst.getInstallmentType().name())
                    .requiredAmountUsd(inst.getRequiredAmount())
                    .requiredAmountVnd(inst.getRequiredAmount().multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP))
                    .paidAmountUsd(paidForInst)
                    .remainingAmountUsd(remInst)
                    .remainingAmountVnd(remInst.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP))
                    .dueAt(inst.getDueAt())
                    .status(inst.getStatus().name())
                    .isPaid(isPaid)
                    .isCurrentDue(isCurrentDue)
                    .description(desc)
                    .build());
        }

        BookingPaymentOptionsResponse.StagePaymentOption stageOption = BookingPaymentOptionsResponse.StagePaymentOption.builder()
                .isAvailable(outstanding.compareTo(BigDecimal.ZERO) > 0 && booking.getBookingStatus() != BookingStatus.Cancelled)
                .totalStages(installments.size())
                .stages(stageDetails)
                .build();

        PaymentPlan plan = null;
        if (installments.size() == 1) plan = PaymentPlan.FULL;
        else if (installments.size() == 2) plan = PaymentPlan.DEPOSIT;
        else if (installments.size() >= 3) plan = PaymentPlan.STAGES;

        return BookingPaymentOptionsResponse.builder()
                .bookingId(bookingId)
                .bookingReference(booking.getBookingReference())
                .bookingStatus(booking.getBookingStatus())
                .currency(booking.getCurrency())
                .agreedFareAmount(agreedFare)
                .agreedFareAmountVnd(agreedFare.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP))
                .totalPaidAmount(totalPaid)
                .totalPaidAmountVnd(totalPaid.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP))
                .outstandingBalance(outstanding)
                .outstandingBalanceVnd(fullVnd)
                .exchangeRate(exchangeRate)
                .configuredPaymentPlan(plan)
                .fullPaymentOption(fullOption)
                .depositPaymentOption(depositOption)
                .stagePaymentOption(stageOption)
                .build();
    }

    /**
     * Tạo yêu cầu thanh toán qua SePay và sinh mã VietQR theo 1 trong 3 hình thức (FULL, DEPOSIT, STAGE).
     */
    @Transactional
    public SepayCheckoutResponse createCheckout(SepayCheckoutRequest request, UUID userId, boolean canReadAll) {
        log.info("Creating SePay checkout for booking {} with payment type {}", request.getBookingId(), request.getPaymentType());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + request.getBookingId()));

        assertAccess(booking, userId, canReadAll);

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException("BOOKING_CANCELLED", "Không thể tạo thanh toán cho đơn đặt chỗ đã bị hủy");
        }
        if (booking.getBookingStatus() == BookingStatus.Draft && booking.getHoldExpiresAt() != null
                && Instant.now().isAfter(booking.getHoldExpiresAt())) {
            throw new BusinessException("BOOKING_HOLD_EXPIRED", "Thời gian giữ chỗ của đơn đã hết hạn. Vui lòng tạo đơn mới.");
        }

        BigDecimal exchangeRate = request.getCustomExchangeRate() != null && request.getCustomExchangeRate().compareTo(BigDecimal.ZERO) > 0
                ? request.getCustomExchangeRate()
                : sepayProperties.getUsdToVndRate();

        // Tính số dư còn lại
        BigDecimal agreedFare = booking.getAgreedFareAmount() != null ? booking.getAgreedFareAmount() : BigDecimal.ZERO;
        BigDecimal totalPaid = paymentRepository.findForBooking(booking.getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getConfirmedAmount() != null)
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = agreedFare.subtract(totalPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "BOOKING_ALREADY_PAID",
                    "Đơn đặt chỗ này đã được thanh toán toàn bộ!");
        }

        List<Installment> installments = installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(booking.getBookingId());

        BigDecimal amountUsd;
        Installment targetInstallment = null;
        String stageDesc;

        switch (request.getPaymentType()) {
            case FULL -> {
                amountUsd = outstanding;
                // Gán installment mục tiêu là đợt chưa trả đầu tiên
                targetInstallment = findFirstUnpaidInstallment(booking.getBookingId(), installments);
                stageDesc = "Thanh toán toàn bộ 100% số dư còn lại";
            }
            case DEPOSIT -> {
                if (installments.isEmpty()) {
                    amountUsd = booking.getRequiredDepositAmount() != null ? booking.getRequiredDepositAmount() : outstanding;
                    stageDesc = "Thanh toán đặt cọc";
                } else {
                    Installment inst1 = installments.get(0);
                    BigDecimal paidForInst1 = getPaidAmountForInstallment(booking.getBookingId(), inst1.getInstallmentId());
                    BigDecimal remainingForDeposit = inst1.getRequiredAmount().subtract(paidForInst1);
                    if (remainingForDeposit.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "DEPOSIT_ALREADY_PAID",
                                "Tiền cọc cho đơn đặt chỗ này đã được thanh toán trước đó!");
                    }
                    amountUsd = remainingForDeposit;
                    targetInstallment = inst1;
                    stageDesc = "Thanh toán đặt cọc (Đợt 1)";
                }
            }
            case STAGE -> {
                if (installments.isEmpty()) {
                    amountUsd = outstanding;
                    stageDesc = "Thanh toán tour";
                } else {
                    if (request.getInstallmentId() != null) {
                        targetInstallment = installmentRepository.findById(request.getInstallmentId())
                                .orElseThrow(() -> new ResourceNotFoundException("Installment not found with id: " + request.getInstallmentId()));
                        if (!targetInstallment.getBooking().getBookingId().equals(booking.getBookingId())) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST, "INSTALLMENT_BOOKING_MISMATCH",
                                    "Đợt thanh toán không thuộc đơn đặt tour này");
                        }
                    } else if (request.getSequenceNo() != null) {
                        targetInstallment = installments.stream()
                                .filter(i -> i.getSequenceNo().equals(request.getSequenceNo()))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "STAGE_NOT_FOUND",
                                        "Không tìm thấy đợt thanh toán số " + request.getSequenceNo()));
                    } else {
                        targetInstallment = findFirstUnpaidInstallment(booking.getBookingId(), installments);
                        if (targetInstallment == null) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST, "ALL_STAGES_PAID",
                                    "Tất cả các đợt thanh toán của đơn đặt tour này đã hoàn tất!");
                        }
                    }

                    BigDecimal paidForTarget = getPaidAmountForInstallment(booking.getBookingId(), targetInstallment.getInstallmentId());
                    BigDecimal remainingForStage = targetInstallment.getRequiredAmount().subtract(paidForTarget);
                    if (remainingForStage.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "STAGE_ALREADY_PAID",
                                "Đợt thanh toán số " + targetInstallment.getSequenceNo() + " đã được thanh toán đầy đủ!");
                    }
                    amountUsd = remainingForStage;
                    stageDesc = "Thanh toán đợt " + targetInstallment.getSequenceNo() + " (" + targetInstallment.getInstallmentType() + ")";
                }
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_TYPE", "Hình thức thanh toán không hợp lệ");
        }

        BigDecimal amountVnd = amountUsd.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);

        // Sinh mã chuyển khoản chuẩn SePay (chỉ gồm ký tự chữ và số)
        String cleanRef = booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "");
        String stageSuffix = targetInstallment != null ? "S" + targetInstallment.getSequenceNo() : "F";
        String transferContent = "CASMS " + cleanRef + stageSuffix;

        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey().trim()
                : "SEPAY-" + cleanRef + "-" + stageSuffix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        // Kiểm tra nếu lệnh thanh toán tương ứng đã tồn tại và đang Pending
        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
        } else {
            payment = new Payment();
            payment.setBooking(booking);
            payment.setInstallment(targetInstallment);
            payment.setOperationType(PaymentOperationType.Collect);
            payment.setPaymentMethod(PaymentMethod.Sepay);
            payment.setRequestedAmount(amountUsd);
            payment.setCurrency(booking.getCurrency());
            payment.setRequestedAt(Instant.now());
            payment.setStatus(PaymentStatus.Pending);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setInternalReceiptReference(transferContent);
            payment = paymentRepository.save(payment);
        }

        // Tạo URL VietQR SePay
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String qrUrl = String.format(
                "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                sepayProperties.getAccountNumber(),
                sepayProperties.getBankName(),
                amountVnd.toPlainString(),
                encodedContent,
                sepayProperties.getQrTemplate()
        );

        return SepayCheckoutResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .paymentType(request.getPaymentType())
                .installmentId(targetInstallment != null ? targetInstallment.getInstallmentId() : null)
                .sequenceNo(targetInstallment != null ? targetInstallment.getSequenceNo() : null)
                .stageDescription(stageDesc)
                .amountUsd(amountUsd)
                .amountVnd(amountVnd)
                .exchangeRate(exchangeRate)
                .bankName(sepayProperties.getBankName())
                .accountNumber(sepayProperties.getAccountNumber())
                .accountHolderName(sepayProperties.getAccountName())
                .transferContent(transferContent)
                .qrCodeUrl(qrUrl)
                .status(payment.getStatus())
                .createdAt(payment.getRequestedAt())
                .instructions("Mở ứng dụng ngân hàng quét mã QR hoặc chuyển khoản chính xác " + amountVnd
                        + " VNĐ với nội dung '" + transferContent + "'. Hệ thống sẽ tự động xác nhận trong vòng vài giây.")
                .build();
    }

    /**
     * Tạo yêu cầu thanh toán nhanh theo số tiền (Quick Checkout).
     * Lưu bản ghi Payment với trạng thái Pending vào CSDL và sinh mã VietQR.
     */
    @Transactional
    public SepayCheckoutResponse createQuickCheckout(SepayQuickCheckoutRequest request) {
        log.info("Creating SePay quick checkout for amount {} VND", request.getAmountVnd());

        BigDecimal amountVnd = request.getAmountVnd();
        if (amountVnd == null || amountVnd.compareTo(BigDecimal.valueOf(2000)) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Số tiền thanh toán tối thiểu là 2.000 VNĐ");
        }

        BigDecimal exchangeRate = sepayProperties.getUsdToVndRate();
        BigDecimal amountUsd = amountVnd.divide(exchangeRate, 2, RoundingMode.HALF_UP);

        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId()).orElse(null);
        }

        // Tạo hoặc chuẩn hóa nội dung chuyển khoản
        String transferContent;
        if (request.getTransferContent() != null && !request.getTransferContent().isBlank()) {
            transferContent = request.getTransferContent().trim().toUpperCase(Locale.ROOT);
        } else if (booking != null) {
            String cleanRef = booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "");
            transferContent = "CASMS " + cleanRef + "F";
        } else {
            int randomNum = 1000 + (int) (Math.random() * 9000);
            transferContent = "CASMS BK2026" + randomNum + "F";
        }

        // Tu dong tim booking neu transferContent chua ma tham chieu cua mot don dat tour co that
        if (booking == null && transferContent != null) {
            booking = findMatchingBooking(transferContent);
        }

        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey().trim()
                : "QUICK-" + transferContent.replaceAll("[^A-Za-z0-9]", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        // Kiểm tra nếu đã có Payment tương ứng đang Pending
        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
        } else {
            payment = new Payment();
            if (booking != null) {
                payment.setBooking(booking);
            }
            payment.setOperationType(PaymentOperationType.Collect);
            payment.setPaymentMethod(PaymentMethod.Sepay);
            payment.setRequestedAmount(amountUsd);
            payment.setCurrency(booking != null && booking.getCurrency() != null ? booking.getCurrency() : "USD");
            payment.setRequestedAt(Instant.now());
            payment.setStatus(PaymentStatus.Pending);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setInternalReceiptReference(transferContent);
            payment = paymentRepository.save(payment);
        }

        // Tạo URL VietQR SePay
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String qrUrl = String.format(
                "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                sepayProperties.getAccountNumber(),
                sepayProperties.getBankName(),
                amountVnd.toPlainString(),
                encodedContent,
                sepayProperties.getQrTemplate()
        );

        return SepayCheckoutResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking != null ? booking.getBookingId() : null)
                .bookingReference(booking != null ? booking.getBookingReference() : transferContent)
                .paymentType(PaymentType.FULL)
                .stageDescription("Thanh toán nhanh theo số tiền")
                .amountUsd(amountUsd)
                .amountVnd(amountVnd)
                .exchangeRate(exchangeRate)
                .bankName(sepayProperties.getBankName())
                .accountNumber(sepayProperties.getAccountNumber())
                .accountHolderName(sepayProperties.getAccountName())
                .transferContent(transferContent)
                .qrCodeUrl(qrUrl)
                .status(payment.getStatus())
                .createdAt(payment.getRequestedAt())
                .instructions("Mở ứng dụng ngân hàng quét mã QR hoặc chuyển khoản chính xác " + amountVnd
                        + " VNĐ với nội dung '" + transferContent + "'. Hệ thống sẽ tự động xác nhận trong vòng vài giây.")
                .build();
    }

    /**
     * Tiếp nhận và xử lý Webhook biến động số dư từ SePay (Idempotent).
     */
    @Transactional
    public SepayWebhookResponse processWebhook(SepayWebhookPayload payload, String authHeader) {
        log.info("Received SePay webhook: id={}, amount={}, content={}",
                payload.getId(), payload.getTransferAmount(), payload.getContent());

        // 1. Kiểm tra bật / tắt SePay
        if (!sepayProperties.isEnabled()) {
            log.warn("SePay integration is disabled in system config");
            return SepayWebhookResponse.builder()
                    .success(false)
                    .message("SePay integration is disabled")
                    .build();
        }

        // 2. Xác thực API Key nếu có cấu hình
        if (sepayProperties.getApiKey() != null && !sepayProperties.getApiKey().isBlank()) {
            String expected = sepayProperties.getApiKey().trim();
            if (authHeader == null || (!authHeader.contains(expected) && !authHeader.equalsIgnoreCase("Apikey " + expected))) {
                log.error("Unauthorized SePay webhook attempt: invalid API key header");
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_SEPAY_API_KEY", "Khóa xác thực SePay không hợp lệ");
            }
        }

        // 3. Bỏ qua các giao dịch không phải tiền vào ("in")
        if (payload.getTransferType() != null && !"in".equalsIgnoreCase(payload.getTransferType().trim())) {
            log.info("Ignored non-incoming transaction: {}", payload.getTransferType());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Ignored non-incoming transfer")
                    .build();
        }

        // 4. Kiểm tra trùng lặp giao dịch (Idempotent check qua SePay transaction ID)
        if (payload.getId() != null && sepayTransactionRepository.existsBySepayId(payload.getId())) {
            log.info("SePay transaction id {} has already been processed", payload.getId());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Transaction already processed")
                    .build();
        }

        // 5. Trích xuất thông tin tìm Payment hoặc Booking tương ứng
        String content = payload.getContent() != null ? payload.getContent() : "";
        String description = payload.getDescription() != null ? payload.getDescription() : "";
        String fullMemo = (content + " " + description).toUpperCase(Locale.ROOT);

        Payment matchedPayment = findMatchingPayment(fullMemo);
        Booking matchedBooking = null;

        if (matchedPayment != null) {
            matchedBooking = matchedPayment.getBooking();
        } else {
            matchedBooking = findMatchingBooking(fullMemo);
            if (matchedBooking != null) {
                // Nếu khách CK chỉ ghi mã booking mà không tạo checkout trước: tìm pending payment của booking đó
                List<Payment> pendingPayments = paymentRepository.findByBooking_BookingId(matchedBooking.getBookingId()).stream()
                        .filter(p -> p.getStatus() == PaymentStatus.Pending && p.getPaymentMethod() == PaymentMethod.Sepay)
                        .sorted(Comparator.comparing(Payment::getRequestedAt).reversed())
                        .toList();
                if (!pendingPayments.isEmpty()) {
                    matchedPayment = pendingPayments.get(0);
                }
            }
        }

        // 6. Xử lý ghi nhận và khớp thanh toán
        SepayTransaction tx = recordSepayTransaction(payload);

        if (matchedBooking == null && matchedPayment == null) {
            log.warn("Could not match any booking or payment from memo: '{}'", content);
            tx.setStatus(SepayTransactionStatus.UNMATCHED);
            sepayTransactionRepository.save(tx);
            return SepayWebhookResponse.builder()
                    .success(false)
                    .message("Could not identify payment or booking from transfer content")
                    .build();
        }

        if (matchedBooking == null && matchedPayment != null) {
            matchedBooking = matchedPayment.getBooking();
        }

        if (matchedBooking != null) {
            tx.setBooking(matchedBooking);
        }

        // Trường hợp tìm được payment đang pending
        if (matchedPayment != null) {
            tx.setPayment(matchedPayment);

            String bookingRef = matchedBooking != null
                    ? matchedBooking.getBookingReference()
                    : (matchedPayment.getInternalReceiptReference() != null ? matchedPayment.getInternalReceiptReference() : "N/A");

            BigDecimal exchangeRate = sepayProperties.getUsdToVndRate();
            BigDecimal expectedVnd = matchedPayment.getRequestedAmount().multiply(exchangeRate);
            BigDecimal receivedVnd = payload.getTransferAmount() != null ? payload.getTransferAmount() : BigDecimal.ZERO;

            // Kiểm tra dung sai số tiền
            double diff = Math.abs(receivedVnd.subtract(expectedVnd).doubleValue());
            double maxAllowedDiff = expectedVnd.doubleValue() * (sepayProperties.getTolerancePercent() / 100.0);

            if (receivedVnd.compareTo(expectedVnd) < 0 && diff > maxAllowedDiff) {
                log.warn("Transfer amount mismatch: received {} VND, expected {} VND", receivedVnd, expectedVnd);
                tx.setStatus(SepayTransactionStatus.AMOUNT_MISMATCH);
                sepayTransactionRepository.save(tx);
                return SepayWebhookResponse.builder()
                        .success(false)
                        .message("Transfer amount is insufficient. Received: " + receivedVnd + ", expected: " + expectedVnd)
                        .paymentId(matchedPayment.getPaymentId())
                        .bookingReference(bookingRef)
                        .build();
            }

            // Xác nhận thanh toán thành công
            String processorRef = resolveProcessorReference(payload);
            PaymentConfirmRequest confirmReq = new PaymentConfirmRequest(matchedPayment.getRequestedAmount(), processorRef);
            paymentService.confirmPayment(matchedPayment.getPaymentId(), confirmReq);

            tx.setStatus(SepayTransactionStatus.PROCESSED);
            tx.setMatchedAt(Instant.now());
            sepayTransactionRepository.save(tx);

            log.info("Successfully confirmed payment {} for booking {}", matchedPayment.getPaymentId(), bookingRef);
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Payment confirmed successfully")
                    .paymentId(matchedPayment.getPaymentId())
                    .bookingReference(bookingRef)
                    .build();
        }

        // Trường hợp khách chuyển trực tiếp không có pending payment: tự động tạo payment mới cho đợt tiếp theo
        BigDecimal receivedVnd = payload.getTransferAmount() != null ? payload.getTransferAmount() : BigDecimal.ZERO;
        BigDecimal exchangeRate = sepayProperties.getUsdToVndRate();
        BigDecimal receivedUsd = receivedVnd.divide(exchangeRate, 4, RoundingMode.HALF_UP);

        List<Installment> installments = installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(matchedBooking.getBookingId());
        Installment nextUnpaid = findFirstUnpaidInstallment(matchedBooking.getBookingId(), installments);

        Payment newPayment = new Payment();
        newPayment.setBooking(matchedBooking);
        newPayment.setInstallment(nextUnpaid);
        newPayment.setOperationType(PaymentOperationType.Collect);
        newPayment.setPaymentMethod(PaymentMethod.Sepay);
        newPayment.setRequestedAmount(receivedUsd);
        newPayment.setCurrency(matchedBooking.getCurrency());
        newPayment.setRequestedAt(Instant.now());
        newPayment.setStatus(PaymentStatus.Pending);
        newPayment.setIdempotencyKey("SEPAY-AUTO-" + payload.getId());
        newPayment.setInternalReceiptReference(content);
        newPayment = paymentRepository.save(newPayment);

        String processorRef = resolveProcessorReference(payload);
        paymentService.confirmPayment(newPayment.getPaymentId(), new PaymentConfirmRequest(receivedUsd, processorRef));

        tx.setPayment(newPayment);
        tx.setStatus(SepayTransactionStatus.PROCESSED);
        tx.setMatchedAt(Instant.now());
        sepayTransactionRepository.save(tx);

        log.info("Created and confirmed automatic payment {} for booking {}", newPayment.getPaymentId(), matchedBooking.getBookingReference());
        return SepayWebhookResponse.builder()
                .success(true)
                .message("Automatic payment created and confirmed")
                .paymentId(newPayment.getPaymentId())
                .bookingReference(matchedBooking.getBookingReference())
                .build();
    }

    /**
     * Tra cứu trạng thái thanh toán của một giao dịch theo paymentId.
     */
    @Transactional(readOnly = true)
    public PaymentStatusCheckResponse getPaymentStatus(UUID paymentId, UUID userId, boolean canReadAll) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        if (payment.getBooking() != null) {
            assertAccess(payment.getBooking(), userId, canReadAll);
        }

        BigDecimal agreedFare = payment.getBooking() != null && payment.getBooking().getAgreedFareAmount() != null
                ? payment.getBooking().getAgreedFareAmount()
                : BigDecimal.ZERO;

        BigDecimal totalPaid = payment.getBooking() != null
                ? paymentRepository.findForBooking(payment.getBooking().getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getConfirmedAmount() != null)
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;

        BigDecimal remaining = agreedFare.subtract(totalPaid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        boolean isConfirmed = (payment.getBooking() != null && payment.getBooking().getBookingStatus() == BookingStatus.Confirmed)
                || payment.getStatus() == PaymentStatus.Confirmed;

        return PaymentStatusCheckResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(payment.getBooking() != null ? payment.getBooking().getBookingId() : null)
                .bookingReference(payment.getBooking() != null ? payment.getBooking().getBookingReference() : payment.getInternalReceiptReference())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .requestedAmount(payment.getRequestedAmount())
                .confirmedAmount(payment.getConfirmedAmount())
                .currency(payment.getCurrency())
                .requestedAt(payment.getRequestedAt())
                .confirmedAt(payment.getConfirmedAt())
                .processorReference(payment.getProcessorReference())
                .bookingStatus(payment.getBooking() != null ? payment.getBooking().getBookingStatus() : null)
                .isBookingConfirmed(isConfirmed)
                .remainingBookingBalance(remaining)
                .build();
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private BigDecimal getPaidAmountForInstallment(UUID bookingId, UUID installmentId) {
        return paymentRepository.findForBooking(bookingId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.Confirmed
                        && p.getOperationType() == PaymentOperationType.Collect
                        && p.getInstallment() != null
                        && p.getInstallment().getInstallmentId().equals(installmentId)
                        && p.getConfirmedAmount() != null)
                .map(Payment::getConfirmedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Installment findFirstUnpaidInstallment(UUID bookingId, List<Installment> installments) {
        for (Installment inst : installments) {
            BigDecimal paid = getPaidAmountForInstallment(bookingId, inst.getInstallmentId());
            if (paid.compareTo(inst.getRequiredAmount()) < 0) {
                return inst;
            }
        }
        return installments.isEmpty() ? null : installments.get(installments.size() - 1);
    }

    private Payment findMatchingPayment(String memo) {
        // Tìm kiếm các pending payment với internalReceiptReference có trong nội dung
        List<Payment> pendingPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.Pending && p.getPaymentMethod() == PaymentMethod.Sepay)
                .toList();

        for (Payment p : pendingPayments) {
            if (p.getInternalReceiptReference() != null && !p.getInternalReceiptReference().isBlank()) {
                String cleanReceipt = p.getInternalReceiptReference().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
                String cleanMemo = memo.replaceAll("[^A-Za-z0-9]", "");
                if (cleanMemo.contains(cleanReceipt)) {
                    return p;
                }
            }
            if (p.getIdempotencyKey() != null && memo.contains(p.getIdempotencyKey().toUpperCase(Locale.ROOT))) {
                return p;
            }
        }
        return null;
    }

    private Booking findMatchingBooking(String memo) {
        // Regex tìm mã đơn đặt tour: BK-YYYY-XXXX hoặc BK...
        Pattern pattern = Pattern.compile("BK-?[0-9]{4}-?[0-9]{3,6}", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(memo);
        if (matcher.find()) {
            String candidate = matcher.group();
            Optional<Booking> directOpt = bookingRepository.findByBookingReference(candidate);
            if (directOpt.isPresent()) return directOpt.get();

            // Thử chuẩn hóa dạng BK-YYYY-XXXX
            String cleanDigits = candidate.replaceAll("[^0-9]", "");
            if (cleanDigits.length() >= 8) {
                String normalized = "BK-" + cleanDigits.substring(0, 4) + "-" + cleanDigits.substring(4);
                Optional<Booking> normOpt = bookingRepository.findByBookingReference(normalized);
                if (normOpt.isPresent()) return normOpt.get();
            }
        }
        return null;
    }

    private SepayTransaction recordSepayTransaction(SepayWebhookPayload payload) {
        SepayTransaction tx = new SepayTransaction();
        tx.setSepayId(payload.getId() != null ? payload.getId() : System.currentTimeMillis());
        tx.setGateway(payload.getGateway());
        tx.setAccountNumber(payload.getAccountNumber());
        tx.setTransferType(payload.getTransferType());
        tx.setTransferAmount(payload.getTransferAmount() != null ? payload.getTransferAmount() : BigDecimal.ZERO);
        tx.setAccumulated(payload.getAccumulated());
        tx.setCode(payload.getCode());
        tx.setContent(payload.getContent());
        tx.setReferenceCode(payload.getReferenceCode());
        tx.setDescription(payload.getDescription());
        tx.setStatus(SepayTransactionStatus.UNMATCHED);
        tx.setCreatedAt(Instant.now());

        if (payload.getTransactionDate() != null && !payload.getTransactionDate().isBlank()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(payload.getTransactionDate().trim(), SEPAY_DATE_FORMATTER);
                tx.setTransactionDate(ldt.toInstant(ZoneOffset.ofHours(7))); // Vietnam GMT+7
            } catch (Exception e) {
                tx.setTransactionDate(Instant.now());
            }
        } else {
            tx.setTransactionDate(Instant.now());
        }

        return sepayTransactionRepository.save(tx);
    }

    private String resolveProcessorReference(SepayWebhookPayload payload) {
        if (payload.getReferenceCode() != null && !payload.getReferenceCode().isBlank()) {
            return payload.getReferenceCode().trim();
        }
        if (payload.getId() != null) {
            return "SEPAY-" + payload.getId();
        }
        return "SEPAY-TX-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void assertAccess(Booking booking, UUID userId, boolean canReadAll) {
        if (!canReadAll && userId != null && booking.getBookedByUser() != null
                && !userId.equals(booking.getBookedByUser().getUserId())) {
            throw new AccessDeniedException("Bạn chỉ có thể xem hoặc thực hiện thanh toán cho đơn của chính mình");
        }
    }
}

