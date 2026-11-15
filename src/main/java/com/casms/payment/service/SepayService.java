package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.service.BookingService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.payment.config.SepayProperties;
import com.casms.payment.dto.request.CardSurchargeConfigRequest;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.SepayCardCheckoutRequest;
import com.casms.payment.dto.request.SepayCheckoutRequest;
import com.casms.payment.dto.request.SepayQuickCheckoutRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.BookingPaymentOptionsResponse;
import com.casms.payment.dto.response.PaymentStatusCheckResponse;
import com.casms.payment.dto.response.SepayCheckoutResponse;
import com.casms.payment.dto.response.SepayConfigResponse;
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

    private static final DateTimeFormatter SEPAY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Lấy thông tin cấu hình SePay hiện hành (Hỗ trợ VND, USD, VietQR, Visa, Mastercard). */
    @Transactional(readOnly = true)
    public SepayConfigResponse getSepayConfig() {
        return SepayConfigResponse.builder()
                .enabled(sepayProperties.isEnabled())
                .cardEnabled(isCardPaymentEnabled())
                .defaultCurrency(resolveDefaultCurrency())
                .supportedCurrencies(resolveSupportedCurrencies())
                .supportedCardBrands(resolveSupportedCardBrands())
                .supportedPaymentChannels(resolveSupportedPaymentChannels())
                .usdToVndRate(resolveDefaultExchangeRate())
                .bankName(sepayProperties.getBankName())
                .accountNumber(sepayProperties.getAccountNumber())
                .accountName(sepayProperties.getAccountName())
                .merchantId(resolveMerchantId())
                .cardGatewayUrl(resolveCardGatewayUrl())
                .threeDSecureEnabled(sepayProperties.isThreeDSecureEnabled())
                .cardSurchargePercent(sepayProperties.getCardSurchargePercent())
                .domesticCardSurchargePercent(sepayProperties.getDomesticCardSurchargePercent())
                .cardSurchargeEnabled(sepayProperties.isCardSurchargeEnabled())
                .brandSurchargeRates(sepayProperties.getBrandSurchargeRates() != null
                        ? new HashMap<>(sepayProperties.getBrandSurchargeRates())
                        : Collections.emptyMap())
                .build();
    }

    /** Cập nhật tỷ lệ phụ phí quẹt thẻ linh hoạt tại runtime (Admin / Manager). */
    public SepayConfigResponse updateSurchargeConfig(
            CardSurchargeConfigRequest request, UUID userId, boolean canManage) {
        if (!canManage) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Bạn không có quyền thay đổi cấu hình phụ phí thanh toán");
        }
        if (request == null) {
            return getSepayConfig();
        }
        if (request.getCardSurchargeEnabled() != null) {
            sepayProperties.setCardSurchargeEnabled(request.getCardSurchargeEnabled());
        }
        if (request.getCardSurchargePercent() != null) {
            sepayProperties.setCardSurchargePercent(request.getCardSurchargePercent());
        }
        if (request.getDomesticCardSurchargePercent() != null) {
            sepayProperties.setDomesticCardSurchargePercent(request.getDomesticCardSurchargePercent());
        }
        if (request.getBrandSurchargeRates() != null && sepayProperties.getBrandSurchargeRates() != null) {
            request.getBrandSurchargeRates().forEach((brand, rate) -> {
                if (brand != null && rate != null) {
                    sepayProperties.getBrandSurchargeRates().put(brand.trim().toUpperCase(Locale.ROOT), rate);
                }
            });
        }
        log.info("Card surcharge config updated by user: {}", userId);
        return getSepayConfig();
    }

    /** Lấy chi tiết 3 hình thức thanh toán (FULL, DEPOSIT, STAGE) của một đơn đặt tour. */
    @Transactional(readOnly = true)
    public BookingPaymentOptionsResponse getPaymentOptions(
            UUID bookingId, UUID userId, boolean canReadAll) {
        Booking booking =
                bookingRepository
                        .findById(bookingId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Booking not found with id: " + bookingId));

        assertAccess(booking, userId, canReadAll);

        BigDecimal agreedFare =
                booking.getAgreedFareAmount() != null
                        ? booking.getAgreedFareAmount()
                        : BigDecimal.ZERO;
        BigDecimal exchangeRate = resolveDefaultExchangeRate();
        boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());

        BigDecimal totalPaid =
                paymentRepository.findForBooking(bookingId).stream()
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.Confirmed
                                                && p.getOperationType()
                                                        == PaymentOperationType.Collect
                                                && p.getConfirmedAmount() != null)
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = agreedFare.subtract(totalPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) < 0) {
            outstanding = BigDecimal.ZERO;
        }

        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId);

        // 1. Tuỳ chọn FULL: Thanh toán toàn bộ số tiền còn lại
        boolean fullAvailable =
                outstanding.compareTo(BigDecimal.ZERO) > 0
                        && booking.getBookingStatus() != BookingStatus.Cancelled;
        BigDecimal fullUsd =
                isBookingVnd
                        ? outstanding.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                        : outstanding;
        BigDecimal fullVnd =
                isBookingVnd
                        ? outstanding.setScale(0, RoundingMode.HALF_UP)
                        : outstanding.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
        BookingPaymentOptionsResponse.FullPaymentOption fullOption =
                BookingPaymentOptionsResponse.FullPaymentOption.builder()
                        .isAvailable(fullAvailable)
                        .amountUsd(fullUsd)
                        .amountVnd(fullVnd)
                        .description(
                                "Thanh toán toàn bộ 100% số tiền còn lại ("
                                        + outstanding
                                        + " "
                                        + booking.getCurrency()
                                        + ")")
                        .build();

        Map<UUID, BigDecimal> installmentPaidMap =
                calculateWaterfallInstallmentPaidAmounts(bookingId, installments);

        // 2. Tuỳ chọn DEPOSIT: Đặt cọc (Đợt 1)
        BookingPaymentOptionsResponse.DepositPaymentOption depositOption = null;
        if (!installments.isEmpty()) {
            Installment inst1 = installments.get(0);
            BigDecimal paidForInst1 =
                    installmentPaidMap.getOrDefault(inst1.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal remInst1 = inst1.getRequiredAmount().subtract(paidForInst1);
            if (remInst1.compareTo(BigDecimal.ZERO) < 0
                    || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                remInst1 = BigDecimal.ZERO;
            }
            boolean isDepositPaid = remInst1.compareTo(BigDecimal.ZERO) <= 0;
            boolean isDepositAvail =
                    !isDepositPaid
                            && outstanding.compareTo(BigDecimal.ZERO) > 0
                            && booking.getBookingStatus() != BookingStatus.Cancelled;

            BigDecimal reqInst1Usd =
                    isBookingVnd
                            ? inst1.getRequiredAmount().divide(exchangeRate, 4, RoundingMode.HALF_UP)
                            : inst1.getRequiredAmount();
            BigDecimal reqInst1Vnd =
                    isBookingVnd
                            ? inst1.getRequiredAmount().setScale(0, RoundingMode.HALF_UP)
                            : inst1.getRequiredAmount()
                                    .multiply(exchangeRate)
                                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal remInst1Usd =
                    isBookingVnd
                            ? remInst1.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                            : remInst1;
            BigDecimal remInst1Vnd =
                    isBookingVnd
                            ? remInst1.setScale(0, RoundingMode.HALF_UP)
                            : remInst1.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);

            depositOption =
                    BookingPaymentOptionsResponse.DepositPaymentOption.builder()
                            .isAvailable(isDepositAvail)
                            .isPaid(isDepositPaid)
                            .installmentId(inst1.getInstallmentId())
                            .sequenceNo(inst1.getSequenceNo())
                            .requiredAmountUsd(reqInst1Usd)
                            .requiredAmountVnd(reqInst1Vnd)
                            .paidAmountUsd(paidForInst1)
                            .remainingAmountUsd(remInst1Usd)
                            .remainingAmountVnd(remInst1Vnd)
                            .dueAt(inst1.getDueAt())
                            .description(
                                    "Đặt cọc giữ chỗ ("
                                            + inst1.getRequiredAmount()
                                            + " "
                                            + booking.getCurrency()
                                            + ")")
                            .build();
        }

        // 3. Tuỳ chọn STAGE: Thanh toán theo từng giai đoạn
        List<BookingPaymentOptionsResponse.StageDetail> stageDetails = new ArrayList<>();
        boolean foundCurrentDue = false;
        for (Installment inst : installments) {
            BigDecimal paidForInst =
                    installmentPaidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal remInst = inst.getRequiredAmount().subtract(paidForInst);
            if (remInst.compareTo(BigDecimal.ZERO) < 0
                    || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                remInst = BigDecimal.ZERO;
            }
            boolean isPaid = remInst.compareTo(BigDecimal.ZERO) <= 0;
            boolean isCurrentDue = false;
            if (!isPaid && !foundCurrentDue && outstanding.compareTo(BigDecimal.ZERO) > 0) {
                isCurrentDue = true;
                foundCurrentDue = true;
            }

            String desc;
            if (installments.size() == 1) {
                desc = "Đợt duy nhất: 100% tiền vé";
            } else if (installments.size() == 2) {
                desc =
                        inst.getSequenceNo() == 1
                                ? "Đợt 1: Đặt cọc 30%"
                                : "Đợt 2: Thanh toán 70% còn lại";
            } else {
                desc =
                        switch (inst.getSequenceNo()) {
                            case 1 -> "Đợt 1: Đặt cọc 30%";
                            case 2 -> "Đợt 2: Thanh toán giữa kỳ 40%";
                            default -> "Đợt " + inst.getSequenceNo() + ": Thanh toán 30% còn lại";
                        };
            }

            BigDecimal reqStageUsd =
                    isBookingVnd
                            ? inst.getRequiredAmount().divide(exchangeRate, 4, RoundingMode.HALF_UP)
                            : inst.getRequiredAmount();
            BigDecimal reqStageVnd =
                    isBookingVnd
                            ? inst.getRequiredAmount().setScale(0, RoundingMode.HALF_UP)
                            : inst.getRequiredAmount()
                                    .multiply(exchangeRate)
                                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal remStageUsd =
                    isBookingVnd
                            ? remInst.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                            : remInst;
            BigDecimal remStageVnd =
                    isBookingVnd
                            ? remInst.setScale(0, RoundingMode.HALF_UP)
                            : remInst.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);

            stageDetails.add(
                    BookingPaymentOptionsResponse.StageDetail.builder()
                            .installmentId(inst.getInstallmentId())
                            .sequenceNo(inst.getSequenceNo())
                            .installmentType(inst.getInstallmentType().name())
                            .requiredAmountUsd(reqStageUsd)
                            .requiredAmountVnd(reqStageVnd)
                            .paidAmountUsd(paidForInst)
                            .remainingAmountUsd(remStageUsd)
                            .remainingAmountVnd(remStageVnd)
                            .dueAt(inst.getDueAt())
                            .status(inst.getStatus().name())
                            .isPaid(isPaid)
                            .isCurrentDue(isCurrentDue)
                            .description(desc)
                            .build());
        }

        BookingPaymentOptionsResponse.StagePaymentOption stageOption =
                BookingPaymentOptionsResponse.StagePaymentOption.builder()
                        .isAvailable(
                                outstanding.compareTo(BigDecimal.ZERO) > 0
                                         && booking.getBookingStatus() != BookingStatus.Cancelled)
                        .totalStages(installments.size())
                        .stages(stageDetails)
                        .build();

        PaymentPlan plan = null;
        if (installments.size() == 1) plan = PaymentPlan.FULL;
        else if (installments.size() == 2) plan = PaymentPlan.DEPOSIT;
        else if (installments.size() >= 3) plan = PaymentPlan.STAGES;

        BigDecimal agreedFareUsd =
                isBookingVnd
                        ? agreedFare.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                        : agreedFare;
        BigDecimal agreedFareVnd =
                isBookingVnd
                        ? agreedFare.setScale(0, RoundingMode.HALF_UP)
                        : agreedFare.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalPaidUsd =
                isBookingVnd
                        ? totalPaid.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                        : totalPaid;
        BigDecimal totalPaidVnd =
                isBookingVnd
                        ? totalPaid.setScale(0, RoundingMode.HALF_UP)
                        : totalPaid.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);

        return BookingPaymentOptionsResponse.builder()
                .bookingId(bookingId)
                .bookingReference(booking.getBookingReference())
                .bookingStatus(booking.getBookingStatus())
                .currency(booking.getCurrency())
                .agreedFareAmount(agreedFareUsd)
                .agreedFareAmountVnd(agreedFareVnd)
                .totalPaidAmount(totalPaidUsd)
                .totalPaidAmountVnd(totalPaidVnd)
                .outstandingBalance(fullUsd)
                .outstandingBalanceVnd(fullVnd)
                .exchangeRate(exchangeRate)
                .supportedCurrencies(resolveSupportedCurrencies())
                .supportedPaymentChannels(resolveSupportedPaymentChannels())
                .supportedCardBrands(resolveSupportedCardBrands())
                .configuredPaymentPlan(plan)
                .fullPaymentOption(fullOption)
                .depositPaymentOption(depositOption)
                .stagePaymentOption(stageOption)
                .build();
    }

    /**
     * Tạo yêu cầu thanh toán qua SePay và sinh mã VietQR theo 1 trong 3 hình thức (FULL, DEPOSIT,
     * STAGE).
     *
     * <p>Được thiết kế Idempotent và tái sử dụng bản ghi Pending của cùng một đợt thanh toán nhằm
     * tránh sinh payment rác và tránh lệch trạng thái polling ở Frontend.
     */
    @Transactional
    public SepayCheckoutResponse createCheckout(
            SepayCheckoutRequest request, UUID userId, boolean canReadAll) {
        log.info(
                "Creating SePay checkout for booking {} with payment type {}, currency={}, channel={}",
                request.getBookingId(),
                request.getPaymentType(),
                request.getCurrency(),
                request.getPaymentChannel());

        Booking booking =
                bookingRepository
                        .findById(request.getBookingId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Booking not found with id: "
                                                        + request.getBookingId()));

        assertAccess(booking, userId, canReadAll);

        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(
                    "BOOKING_CANCELLED", "Không thể tạo thanh toán cho đơn đặt chỗ đã bị hủy");
        }
        if (booking.getBookingStatus() == BookingStatus.Draft
                && booking.getHoldExpiresAt() != null
                && Instant.now().isAfter(booking.getHoldExpiresAt())) {
            throw new BusinessException(
                    "BOOKING_HOLD_EXPIRED",
                    "Thời gian giữ chỗ của đơn đã hết hạn. Vui lòng tạo đơn mới.");
        }

        BigDecimal exchangeRate =
                request.getCustomExchangeRate() != null
                                && request.getCustomExchangeRate().compareTo(BigDecimal.ZERO) > 0
                        ? request.getCustomExchangeRate()
                        : resolveDefaultExchangeRate();
        String paymentCurrency =
                normalizeSupportedCurrency(request.getCurrency(), resolveDefaultCurrency());
        String paymentChannel =
                resolvePaymentChannel(
                        request.getPaymentChannel(),
                        request.getCardBrand(),
                        request.getCardNumber(),
                        request.getCardToken());

        // Tính số dư còn lại
        BigDecimal agreedFare =
                booking.getAgreedFareAmount() != null
                        ? booking.getAgreedFareAmount()
                        : BigDecimal.ZERO;
        BigDecimal totalPaid =
                paymentRepository.findForBooking(booking.getBookingId()).stream()
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.Confirmed
                                                && p.getOperationType()
                                                        == PaymentOperationType.Collect
                                                && p.getConfirmedAmount() != null)
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = agreedFare.subtract(totalPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "BOOKING_ALREADY_PAID",
                    "Đơn đặt chỗ này đã được thanh toán toàn bộ!");
        }

        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                        booking.getBookingId());

        BigDecimal bookingBaseAmount;
        Installment targetInstallment = null;
        String stageDesc;

        switch (request.getPaymentType()) {
            case FULL -> {
                bookingBaseAmount = outstanding;
                // Gán installment mục tiêu là đợt chưa trả đầu tiên
                targetInstallment =
                        findFirstUnpaidInstallment(booking.getBookingId(), installments);
                stageDesc = "Thanh toán toàn bộ 100% số dư còn lại";
            }
            case DEPOSIT -> {
                if (installments.isEmpty()) {
                    bookingBaseAmount =
                            booking.getRequiredDepositAmount() != null
                                    ? booking.getRequiredDepositAmount()
                                    : outstanding;
                    stageDesc = "Thanh toán đặt cọc";
                } else {
                    Installment inst1 = installments.get(0);
                    BigDecimal paidForInst1 =
                            getPaidAmountForInstallment(
                                    booking.getBookingId(), inst1.getInstallmentId());
                    BigDecimal remainingForDeposit =
                            inst1.getRequiredAmount().subtract(paidForInst1);
                    if (remainingForDeposit.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "DEPOSIT_ALREADY_PAID",
                                "Tiền cọc cho đơn đặt chỗ này đã được thanh toán trước đó!");
                    }
                    bookingBaseAmount = remainingForDeposit;
                    targetInstallment = inst1;
                    stageDesc = "Thanh toán đặt cọc (Đợt 1)";
                }
            }
            case STAGE -> {
                if (installments.isEmpty()) {
                    bookingBaseAmount = outstanding;
                    stageDesc = "Thanh toán tour";
                } else {
                    if (request.getInstallmentId() != null) {
                        targetInstallment =
                                installmentRepository
                                        .findById(request.getInstallmentId())
                                        .orElseThrow(
                                                () ->
                                                        new ResourceNotFoundException(
                                                                "Installment not found with id: "
                                                                        + request
                                                                                .getInstallmentId()));
                        if (!targetInstallment
                                .getBooking()
                                .getBookingId()
                                .equals(booking.getBookingId())) {
                            throw new BusinessException(
                                    HttpStatus.BAD_REQUEST,
                                    "INSTALLMENT_BOOKING_MISMATCH",
                                    "Đợt thanh toán không thuộc đơn đặt tour này");
                        }
                    } else if (request.getSequenceNo() != null) {
                        targetInstallment =
                                installments.stream()
                                        .filter(
                                                i ->
                                                        i.getSequenceNo()
                                                                .equals(request.getSequenceNo()))
                                        .findFirst()
                                        .orElseThrow(
                                                () ->
                                                        new BusinessException(
                                                                HttpStatus.BAD_REQUEST,
                                                                "STAGE_NOT_FOUND",
                                                                "Không tìm thấy đợt thanh toán số "
                                                                        + request.getSequenceNo()));
                    } else {
                        targetInstallment =
                                findFirstUnpaidInstallment(booking.getBookingId(), installments);
                        if (targetInstallment == null) {
                            throw new BusinessException(
                                    HttpStatus.BAD_REQUEST,
                                    "ALL_STAGES_PAID",
                                    "Tất cả các đợt thanh toán của đơn đặt tour này đã hoàn tất!");
                        }
                    }

                    BigDecimal paidForTarget =
                            getPaidAmountForInstallment(
                                    booking.getBookingId(), targetInstallment.getInstallmentId());
                    BigDecimal remainingForStage =
                            targetInstallment.getRequiredAmount().subtract(paidForTarget);
                    if (remainingForStage.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "STAGE_ALREADY_PAID",
                                "Đợt thanh toán số "
                                        + targetInstallment.getSequenceNo()
                                        + " đã được thanh toán đầy đủ!");
                    }
                    bookingBaseAmount = remainingForStage;
                    stageDesc =
                            "Thanh toán đợt "
                                    + targetInstallment.getSequenceNo()
                                    + " ("
                                    + targetInstallment.getInstallmentType()
                                    + ")";
                }
            }
            default ->
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_PAYMENT_TYPE",
                            "Hình thức thanh toán không hợp lệ");
        }

        boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());
        BigDecimal amountUsd =
                isBookingVnd
                        ? bookingBaseAmount.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                        : bookingBaseAmount;
        BigDecimal amountVnd =
                isBookingVnd
                        ? bookingBaseAmount.setScale(0, RoundingMode.HALF_UP)
                        : bookingBaseAmount.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);

        // Nếu khách chọn thanh toán bằng thẻ quốc tế VISA hoặc MASTERCARD qua SePay
        if (!"VIETQR".equalsIgnoreCase(paymentChannel)) {
            SepayCardCheckoutRequest cardReq =
                    SepayCardCheckoutRequest.builder()
                            .bookingId(booking.getBookingId())
                            .paymentType(request.getPaymentType())
                            .installmentId(request.getInstallmentId())
                            .sequenceNo(request.getSequenceNo())
                            .currency(paymentCurrency)
                            .cardBrand(
                                    request.getCardBrand() != null
                                            ? request.getCardBrand()
                                            : paymentChannel)
                            .cardNumber(request.getCardNumber())
                            .cardHolderName(request.getCardHolderName())
                            .expiryMonth(request.getExpiryMonth())
                            .expiryYear(request.getExpiryYear())
                            .cvv(request.getCvv())
                            .cardToken(request.getCardToken())
                            .customExchangeRate(exchangeRate)
                            .idempotencyKey(request.getIdempotencyKey())
                            .autoCapture(request.getAutoCapture())
                            .build();
            return createCardCheckout(cardReq, userId, canReadAll);
        }

        BigDecimal chargedAmount = "USD".equalsIgnoreCase(paymentCurrency) ? amountUsd : amountVnd;

        // Sinh mã chuyển khoản chuẩn SePay (chỉ gồm ký tự chữ và số)
        String cleanRef = booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "");
        String stageSuffix =
                targetInstallment != null ? "S" + targetInstallment.getSequenceNo() : "F";
        String transferContent = "CASMS " + cleanRef + stageSuffix;

        String defaultIdempotencyKey = "SEPAY-" + cleanRef + "-" + stageSuffix;
        String idempotencyKey =
                (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                        ? request.getIdempotencyKey().trim()
                        : defaultIdempotencyKey;

        // 1. Kiểm tra nếu lệnh thanh toán tương ứng đã tồn tại theo IdempotencyKey
        Optional<Payment> existingPaymentOpt =
                paymentRepository.findByIdempotencyKey(idempotencyKey);
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
            if (payment.getStatus() == PaymentStatus.Confirmed) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PAYMENT_ALREADY_CONFIRMED",
                        "Lệnh thanh toán này đã được xác nhận thành công trước đó!");
            }
            payment.setRequestedAmount(bookingBaseAmount);
            payment.setOriginalAmount(chargedAmount);
            payment.setOriginalCurrency(paymentCurrency);
            payment.setConvertedAmount(chargedAmount);
            payment.setSettlementCurrency(paymentCurrency);
            payment.setExchangeRateApplied(exchangeRate);
            payment.setExchangeRateSource("SEPAY_CONFIG");
            payment.setExchangeRateTimestamp(Instant.now());
            payment.setProvider("SEPAY_VIETQR");
            payment.setInternalReceiptReference(transferContent);
            payment.setRequestedAt(Instant.now());
            payment = paymentRepository.save(payment);
        } else {
            // 2. Tái sử dụng payment đang Pending của đợt thanh toán này nếu có, tránh sinh bản ghi
            // rác
            final UUID targetInstId =
                    targetInstallment != null ? targetInstallment.getInstallmentId() : null;
            List<Payment> pendingList =
                    paymentRepository.findByBooking_BookingId(booking.getBookingId()).stream()
                            .filter(
                                    p ->
                                            p.getStatus() == PaymentStatus.Pending
                                                    && p.getPaymentMethod() == PaymentMethod.Sepay
                                                    && Objects.equals(
                                                            p.getInstallment() != null
                                                                    ? p.getInstallment()
                                                                            .getInstallmentId()
                                                                    : null,
                                                            targetInstId))
                            .toList();

            if (!pendingList.isEmpty()) {
                payment = pendingList.get(0);
                payment.setRequestedAmount(bookingBaseAmount);
                payment.setOriginalAmount(chargedAmount);
                payment.setOriginalCurrency(paymentCurrency);
                payment.setConvertedAmount(chargedAmount);
                payment.setSettlementCurrency(paymentCurrency);
                payment.setExchangeRateApplied(exchangeRate);
                payment.setExchangeRateSource("SEPAY_CONFIG");
                payment.setExchangeRateTimestamp(Instant.now());
                payment.setProvider("SEPAY_VIETQR");
                payment.setInternalReceiptReference(transferContent);
                payment.setIdempotencyKey(idempotencyKey);
                payment.setRequestedAt(Instant.now());
                payment = paymentRepository.save(payment);
            } else {
                payment = new Payment();
                payment.setBooking(booking);
                payment.setInstallment(targetInstallment);
                payment.setOperationType(PaymentOperationType.Collect);
                payment.setPaymentMethod(PaymentMethod.Sepay);
                payment.setProvider("SEPAY_VIETQR");
                payment.setRequestedAmount(bookingBaseAmount);
                payment.setCurrency(booking.getCurrency());
                payment.setOriginalAmount(chargedAmount);
                payment.setOriginalCurrency(paymentCurrency);
                payment.setConvertedAmount(chargedAmount);
                payment.setSettlementCurrency(paymentCurrency);
                payment.setExchangeRateApplied(exchangeRate);
                payment.setExchangeRateSource("SEPAY_CONFIG");
                payment.setExchangeRateTimestamp(Instant.now());
                payment.setRequestedAt(Instant.now());
                payment.setStatus(PaymentStatus.Pending);
                payment.setIdempotencyKey(idempotencyKey);
                payment.setInternalReceiptReference(transferContent);
                payment = paymentRepository.save(payment);
            }
        }

        // Tạo URL VietQR SePay
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String qrUrl =
                String.format(
                        "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                        sepayProperties.getAccountNumber(),
                        sepayProperties.getBankName(),
                        amountVnd.toPlainString(),
                        encodedContent,
                        sepayProperties.getQrTemplate());

        String instructions =
                "USD".equalsIgnoreCase(paymentCurrency)
                        ? "Thanh toán qua SePay theo đơn vị USD ("
                                + amountUsd
                                + " USD ~ "
                                + amountVnd
                                + " VNĐ) hoặc mở ứng dụng ngân hàng quét mã QR với nội dung '"
                                + transferContent
                                + "'. Hỗ trợ thanh toán cả bằng thẻ quốc tế Visa / Mastercard."
                        : "Mở ứng dụng ngân hàng quét mã QR hoặc chuyển khoản chính xác "
                                + amountVnd
                                + " VNĐ với nội dung '"
                                + transferContent
                                + "'. Hệ thống sẽ tự động xác nhận trong vòng vài giây.";

        return SepayCheckoutResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .paymentType(request.getPaymentType())
                .installmentId(
                        targetInstallment != null ? targetInstallment.getInstallmentId() : null)
                .sequenceNo(targetInstallment != null ? targetInstallment.getSequenceNo() : null)
                .stageDescription(stageDesc)
                .amountUsd(amountUsd)
                .amountVnd(amountVnd)
                .exchangeRate(exchangeRate)
                .paymentCurrency(paymentCurrency)
                .chargedAmount(chargedAmount)
                .paymentChannel("VIETQR")
                .supportedCurrencies(resolveSupportedCurrencies())
                .supportedCardBrands(resolveSupportedCardBrands())
                .bankName(sepayProperties.getBankName())
                .accountNumber(sepayProperties.getAccountNumber())
                .accountHolderName(sepayProperties.getAccountName())
                .transferContent(transferContent)
                .qrCodeUrl(qrUrl)
                .status(payment.getStatus())
                .createdAt(payment.getRequestedAt())
                .instructions(instructions)
                .build();
    }

    /**
     * Tạo yêu cầu thanh toán nhanh theo số tiền (Quick Checkout) hỗ trợ cả VND (Tiền Việt) và USD,
     * cũng như kênh VietQR hoặc thẻ quốc tế Visa / Mastercard.
     */
    @Transactional
    public SepayCheckoutResponse createQuickCheckout(SepayQuickCheckoutRequest request) {
        BigDecimal exchangeRate =
                request.getCustomExchangeRate() != null
                                && request.getCustomExchangeRate().compareTo(BigDecimal.ZERO) > 0
                        ? request.getCustomExchangeRate()
                        : resolveDefaultExchangeRate();

        // Xác định loại tiền tệ (VND hoặc USD) và số tiền tương ứng
        String paymentCurrency;
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            paymentCurrency = normalizeSupportedCurrency(request.getCurrency(), "VND");
        } else if (request.getAmountUsd() != null && request.getAmountVnd() == null) {
            paymentCurrency = "USD";
        } else {
            paymentCurrency = "VND";
        }

        BigDecimal amountVnd;
        BigDecimal amountUsd;
        if ("USD".equalsIgnoreCase(paymentCurrency)) {
            BigDecimal rawUsd =
                    request.getAmountUsd() != null ? request.getAmountUsd() : request.getAmount();
            if (rawUsd == null && request.getAmountVnd() != null) {
                rawUsd = request.getAmountVnd().divide(exchangeRate, 2, RoundingMode.HALF_UP);
            }
            if (rawUsd == null || rawUsd.compareTo(new BigDecimal("0.01")) < 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_AMOUNT",
                        "Số tiền thanh toán USD tối thiểu là 0.01 USD");
            }
            amountUsd = rawUsd.setScale(2, RoundingMode.HALF_UP);
            amountVnd =
                    request.getAmountVnd() != null
                            ? request.getAmountVnd().setScale(0, RoundingMode.HALF_UP)
                            : amountUsd.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
        } else {
            BigDecimal rawVnd =
                    request.getAmountVnd() != null ? request.getAmountVnd() : request.getAmount();
            if (rawVnd == null && request.getAmountUsd() != null) {
                rawVnd =
                        request.getAmountUsd()
                                .multiply(exchangeRate)
                                .setScale(0, RoundingMode.HALF_UP);
            }
            if (rawVnd == null || rawVnd.compareTo(BigDecimal.valueOf(2000)) < 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_AMOUNT",
                        "Số tiền thanh toán tối thiểu là 2.000 VNĐ");
            }
            amountVnd = rawVnd.setScale(0, RoundingMode.HALF_UP);
            amountUsd =
                    request.getAmountUsd() != null
                            ? request.getAmountUsd()
                            : amountVnd.divide(exchangeRate, 2, RoundingMode.HALF_UP);
        }

        String paymentChannel =
                resolvePaymentChannel(
                        request.getPaymentChannel(),
                        request.getCardBrand(),
                        request.getCardNumber(),
                        request.getCardToken());

        // Nếu khách chọn thanh toán nhanh bằng thẻ quốc tế Visa / Mastercard (bằng VND hoặc USD)
        if (!"VIETQR".equalsIgnoreCase(paymentChannel)) {
            SepayCardCheckoutRequest cardReq =
                    SepayCardCheckoutRequest.builder()
                            .bookingId(request.getBookingId())
                            .paymentType(PaymentType.FULL)
                            .currency(paymentCurrency)
                            .amount(
                                    "USD".equalsIgnoreCase(paymentCurrency)
                                            ? amountUsd
                                            : amountVnd)
                            .amountVnd(amountVnd)
                            .amountUsd(amountUsd)
                            .cardBrand(
                                    request.getCardBrand() != null
                                            ? request.getCardBrand()
                                            : paymentChannel)
                            .cardNumber(request.getCardNumber())
                            .cardHolderName(request.getCardHolderName())
                            .expiryMonth(request.getExpiryMonth())
                            .expiryYear(request.getExpiryYear())
                            .cvv(request.getCvv())
                            .cardToken(request.getCardToken())
                            .customExchangeRate(exchangeRate)
                            .description(request.getDescription())
                            .idempotencyKey(request.getIdempotencyKey())
                            .autoCapture(request.getAutoCapture())
                            .build();
            return createCardCheckout(cardReq, null, true);
        }

        log.info(
                "Creating SePay quick checkout: currency={}, amountVnd={}, amountUsd={}",
                paymentCurrency,
                amountVnd,
                amountUsd);

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

        // Tự động tìm booking nếu transferContent chứa mã tham chiếu của một đơn đặt tour có thật
        if (booking == null && transferContent != null) {
            booking = findMatchingBooking(transferContent);
        }

        String defaultIdempotencyKey = "QUICK-" + transferContent.replaceAll("[^A-Za-z0-9]", "");
        String idempotencyKey =
                (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                        ? request.getIdempotencyKey().trim()
                        : defaultIdempotencyKey;

        BigDecimal chargedAmount = "USD".equalsIgnoreCase(paymentCurrency) ? amountUsd : amountVnd;
        BigDecimal requestedBaseAmount =
                booking != null && "VND".equalsIgnoreCase(booking.getCurrency())
                        ? amountVnd
                        : amountUsd;

        // Kiểm tra nếu đã có Payment tương ứng đang Pending
        Optional<Payment> existingPaymentOpt =
                paymentRepository.findByIdempotencyKey(idempotencyKey);
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
            if (payment.getStatus() == PaymentStatus.Confirmed) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PAYMENT_ALREADY_CONFIRMED",
                        "Lệnh thanh toán nhanh này đã được xác nhận trước đó!");
            }
            payment.setRequestedAmount(requestedBaseAmount);
            payment.setOriginalAmount(chargedAmount);
            payment.setOriginalCurrency(paymentCurrency);
            payment.setConvertedAmount(chargedAmount);
            payment.setSettlementCurrency(paymentCurrency);
            payment.setExchangeRateApplied(exchangeRate);
            payment.setExchangeRateSource("SEPAY_CONFIG");
            payment.setExchangeRateTimestamp(Instant.now());
            payment.setProvider("SEPAY_VIETQR");
            payment.setRequestedAt(Instant.now());
            payment = paymentRepository.save(payment);
        } else {
            payment = new Payment();
            if (booking != null) {
                payment.setBooking(booking);
            }
            payment.setOperationType(PaymentOperationType.Collect);
            payment.setPaymentMethod(PaymentMethod.Sepay);
            payment.setProvider("SEPAY_VIETQR");
            payment.setRequestedAmount(requestedBaseAmount);
            payment.setCurrency(
                    booking != null && booking.getCurrency() != null
                            ? booking.getCurrency()
                            : "USD");
            payment.setOriginalAmount(chargedAmount);
            payment.setOriginalCurrency(paymentCurrency);
            payment.setConvertedAmount(chargedAmount);
            payment.setSettlementCurrency(paymentCurrency);
            payment.setExchangeRateApplied(exchangeRate);
            payment.setExchangeRateSource("SEPAY_CONFIG");
            payment.setExchangeRateTimestamp(Instant.now());
            payment.setRequestedAt(Instant.now());
            payment.setStatus(PaymentStatus.Pending);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setInternalReceiptReference(transferContent);
            payment = paymentRepository.save(payment);
        }

        // Tạo URL VietQR SePay
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String qrUrl =
                String.format(
                        "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                        sepayProperties.getAccountNumber(),
                        sepayProperties.getBankName(),
                        amountVnd.toPlainString(),
                        encodedContent,
                        sepayProperties.getQrTemplate());

        return SepayCheckoutResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking != null ? booking.getBookingId() : null)
                .bookingReference(booking != null ? booking.getBookingReference() : transferContent)
                .paymentType(PaymentType.FULL)
                .stageDescription("Thanh toán nhanh theo số tiền")
                .amountUsd(amountUsd)
                .amountVnd(amountVnd)
                .exchangeRate(exchangeRate)
                .paymentCurrency(paymentCurrency)
                .chargedAmount(chargedAmount)
                .paymentChannel("VIETQR")
                .supportedCurrencies(resolveSupportedCurrencies())
                .supportedCardBrands(resolveSupportedCardBrands())
                .bankName(sepayProperties.getBankName())
                .accountNumber(sepayProperties.getAccountNumber())
                .accountHolderName(sepayProperties.getAccountName())
                .transferContent(transferContent)
                .qrCodeUrl(qrUrl)
                .status(payment.getStatus())
                .createdAt(payment.getRequestedAt())
                .instructions(
                        "Mở ứng dụng ngân hàng quét mã QR hoặc chuyển khoản chính xác "
                                + amountVnd
                                + " VNĐ với nội dung '"
                                + transferContent
                                + "'. Hệ thống sẽ tự động xác nhận trong vòng vài giây.")
                .build();
    }

    /**
     * Thanh toán qua cổng SePay bằng thẻ quốc tế **VISA** hoặc **MASTERCARD**, hỗ trợ cả **Tiền
     * Việt (VND)** và **Đô la Mỹ (USD)**.
     *
     * <ul>
     *   <li>Hỗ trợ thanh toán theo đơn đặt chỗ (`bookingId` + `FULL` / `DEPOSIT` / `STAGE`) hoặc
     *       thanh toán nhanh theo số tiền (`amount` / `amountVnd` / `amountUsd`).
     *   <li>Tự động nhận diện thương hiệu thẻ `VISA` (đầu số `4`) hoặc `MASTERCARD` (đầu số
     *       `51-55`, `2221-2720`) hoặc nhận từ `cardBrand` / `cardToken`.
     *   <li>Tuân thủ chuẩn bảo mật PCI-DSS: không lưu số thẻ gốc (PAN) hay mã CVV xuống CSDL; chỉ
     *       lưu 4 số cuối đã che (`**** **** **** 4242`) và `paymentToken`.
     *   <li>Nếu `autoCapture = true` (mặc định khi có thông tin thẻ/token), tự động uỷ quyền &
     *       gạch nợ ngay, tạo bản ghi đối soát `SepayTransaction`, xác nhận `Payment`, và phân bổ
     *       tiền thừa (surplus) theo cơ chế Waterfall cho các đợt trả góp tiếp theo.
     * </ul>
     */
    @Transactional
    public SepayCheckoutResponse createCardCheckout(
            SepayCardCheckoutRequest request, UUID userId, boolean canReadAll) {
        if (!sepayProperties.isEnabled()) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEPAY_DISABLED",
                    "Cổng thanh toán SePay hiện đang tạm ngưng");
        }
        if (!isCardPaymentEnabled()) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEPAY_CARD_DISABLED",
                    "Tính năng thanh toán thẻ quốc tế Visa/Mastercard qua SePay đang tạm khóa");
        }

        BigDecimal exchangeRate =
                request.getCustomExchangeRate() != null
                                && request.getCustomExchangeRate().compareTo(BigDecimal.ZERO) > 0
                        ? request.getCustomExchangeRate()
                        : resolveDefaultExchangeRate();

        // 1. Xác định đơn vị tiền tệ thanh toán: VND (Tiền Việt) hoặc USD
        String rawCurrency = request.getCurrency();
        if ((rawCurrency == null || rawCurrency.isBlank())) {
            if (request.getAmountUsd() != null && request.getAmountVnd() == null) {
                rawCurrency = "USD";
            } else if (request.getAmountVnd() != null && request.getAmountUsd() == null) {
                rawCurrency = "VND";
            } else {
                rawCurrency = resolveDefaultCurrency();
            }
        }
        String paymentCurrency = normalizeSupportedCurrency(rawCurrency, "VND");

        // 2. Xác thực thương hiệu thẻ quốc tế (VISA hoặc MASTERCARD) & che số thẻ PCI-DSS
        String cardBrand =
                resolveAndValidateCardBrand(
                        request.getCardBrand(), request.getCardNumber(), request.getCardToken());
        String maskedCard = maskCardNumber(request.getCardNumber(), request.getCardToken(), cardBrand);
        String resolvedToken =
                resolveCardToken(request.getCardToken(), request.getCardNumber(), cardBrand);

        // 3. Tính toán số tiền thanh toán từ Booking (nếu có) hoặc từ số tiền trực tiếp
        Booking booking = null;
        Installment targetInstallment = null;
        PaymentType effectivePaymentType =
                request.getPaymentType() != null ? request.getPaymentType() : PaymentType.FULL;
        String stageDesc =
                request.getDescription() != null && !request.getDescription().isBlank()
                        ? request.getDescription()
                        : "Thanh toán bằng thẻ quốc tế " + cardBrand + " (" + paymentCurrency + ")";

        BigDecimal amountUsd;
        BigDecimal amountVnd;
        BigDecimal bookingBaseAmount;

        if (request.getBookingId() != null) {
            booking =
                    bookingRepository
                            .findById(request.getBookingId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Booking not found with id: "
                                                            + request.getBookingId()));
            assertAccess(booking, userId, canReadAll);

            if (booking.getBookingStatus() == BookingStatus.Cancelled) {
                throw new BusinessException(
                        "BOOKING_CANCELLED", "Không thể thanh toán cho đơn đặt chỗ đã bị hủy");
            }
            if (booking.getBookingStatus() == BookingStatus.Draft
                    && booking.getHoldExpiresAt() != null
                    && Instant.now().isAfter(booking.getHoldExpiresAt())) {
                throw new BusinessException(
                        "BOOKING_HOLD_EXPIRED",
                        "Thời gian giữ chỗ của đơn đã hết hạn. Vui lòng tạo đơn mới.");
            }

            BigDecimal agreedFare =
                    booking.getAgreedFareAmount() != null
                            ? booking.getAgreedFareAmount()
                            : BigDecimal.ZERO;
            BigDecimal totalPaid =
                    paymentRepository.findForBooking(booking.getBookingId()).stream()
                            .filter(
                                    p ->
                                            p.getStatus() == PaymentStatus.Confirmed
                                                    && p.getOperationType()
                                                            == PaymentOperationType.Collect
                                                    && p.getConfirmedAmount() != null)
                            .map(Payment::getConfirmedAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal outstanding = agreedFare.subtract(totalPaid);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "BOOKING_ALREADY_PAID",
                        "Đơn đặt chỗ này đã được thanh toán toàn bộ!");
            }

            List<Installment> installments =
                    installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                            booking.getBookingId());

            boolean hasExplicitCustomAmount =
                    request.getAmount() != null
                            || request.getAmountVnd() != null
                            || request.getAmountUsd() != null;

            if (hasExplicitCustomAmount && request.getPaymentType() == null) {
                targetInstallment =
                        findFirstUnpaidInstallment(booking.getBookingId(), installments);
                if ("USD".equalsIgnoreCase(paymentCurrency)) {
                    BigDecimal rawUsd =
                            request.getAmountUsd() != null
                                    ? request.getAmountUsd()
                                    : request.getAmount();
                    amountUsd = rawUsd.setScale(2, RoundingMode.HALF_UP);
                    amountVnd =
                            amountUsd.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
                } else {
                    BigDecimal rawVnd =
                            request.getAmountVnd() != null
                                    ? request.getAmountVnd()
                                    : request.getAmount();
                    amountVnd = rawVnd.setScale(0, RoundingMode.HALF_UP);
                    amountUsd = amountVnd.divide(exchangeRate, 4, RoundingMode.HALF_UP);
                }
                bookingBaseAmount =
                        "VND".equalsIgnoreCase(booking.getCurrency()) ? amountVnd : amountUsd;
            } else {
                switch (effectivePaymentType) {
                    case FULL -> {
                        bookingBaseAmount = outstanding;
                        targetInstallment =
                                findFirstUnpaidInstallment(booking.getBookingId(), installments);
                        stageDesc =
                                "Thanh toán toàn bộ 100% qua thẻ "
                                        + cardBrand
                                        + " ("
                                        + paymentCurrency
                                        + ")";
                    }
                    case DEPOSIT -> {
                        if (installments.isEmpty()) {
                            bookingBaseAmount =
                                    booking.getRequiredDepositAmount() != null
                                            ? booking.getRequiredDepositAmount()
                                            : outstanding;
                            stageDesc =
                                    "Thanh toán đặt cọc qua thẻ "
                                            + cardBrand
                                            + " ("
                                            + paymentCurrency
                                            + ")";
                        } else {
                            Installment inst1 = installments.get(0);
                            BigDecimal paidForInst1 =
                                    getPaidAmountForInstallment(
                                            booking.getBookingId(), inst1.getInstallmentId());
                            BigDecimal remainingForDeposit =
                                    inst1.getRequiredAmount().subtract(paidForInst1);
                            if (remainingForDeposit.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "DEPOSIT_ALREADY_PAID",
                                        "Tiền cọc cho đơn đặt chỗ này đã được thanh toán trước đó!");
                            }
                            bookingBaseAmount = remainingForDeposit;
                            targetInstallment = inst1;
                            stageDesc =
                                    "Thanh toán đặt cọc (Đợt 1) qua thẻ "
                                            + cardBrand
                                            + " ("
                                            + paymentCurrency
                                            + ")";
                        }
                    }
                    case STAGE -> {
                        if (installments.isEmpty()) {
                            bookingBaseAmount = outstanding;
                        } else {
                            if (request.getInstallmentId() != null) {
                                targetInstallment =
                                        installmentRepository
                                                .findById(request.getInstallmentId())
                                                .orElseThrow(
                                                        () ->
                                                                new ResourceNotFoundException(
                                                                        "Installment not found with id: "
                                                                                + request
                                                                                        .getInstallmentId()));
                                if (!targetInstallment
                                        .getBooking()
                                        .getBookingId()
                                        .equals(booking.getBookingId())) {
                                    throw new BusinessException(
                                            HttpStatus.BAD_REQUEST,
                                            "INSTALLMENT_BOOKING_MISMATCH",
                                            "Đợt thanh toán không thuộc đơn đặt tour này");
                                }
                            } else if (request.getSequenceNo() != null) {
                                targetInstallment =
                                        installments.stream()
                                                .filter(
                                                        i ->
                                                                i.getSequenceNo()
                                                                        .equals(
                                                                                request
                                                                                        .getSequenceNo()))
                                                .findFirst()
                                                .orElseThrow(
                                                        () ->
                                                                new BusinessException(
                                                                        HttpStatus.BAD_REQUEST,
                                                                        "STAGE_NOT_FOUND",
                                                                        "Không tìm thấy đợt thanh toán số "
                                                                                + request
                                                                                        .getSequenceNo()));
                            } else {
                                targetInstallment =
                                        findFirstUnpaidInstallment(
                                                booking.getBookingId(), installments);
                                if (targetInstallment == null) {
                                    throw new BusinessException(
                                            HttpStatus.BAD_REQUEST,
                                            "ALL_STAGES_PAID",
                                            "Tất cả các đợt thanh toán của đơn đặt tour này đã hoàn tất!");
                                }
                            }

                            BigDecimal paidForTarget =
                                    getPaidAmountForInstallment(
                                            booking.getBookingId(),
                                            targetInstallment.getInstallmentId());
                            BigDecimal remainingForStage =
                                    targetInstallment.getRequiredAmount().subtract(paidForTarget);
                            if (remainingForStage.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new BusinessException(
                                        HttpStatus.BAD_REQUEST,
                                        "STAGE_ALREADY_PAID",
                                        "Đợt thanh toán số "
                                                + targetInstallment.getSequenceNo()
                                                + " đã được thanh toán đầy đủ!");
                            }
                            bookingBaseAmount = remainingForStage;
                            stageDesc =
                                    "Thanh toán đợt "
                                            + targetInstallment.getSequenceNo()
                                            + " qua thẻ "
                                            + cardBrand
                                            + " ("
                                            + paymentCurrency
                                            + ")";
                        }
                    }
                    default ->
                            throw new BusinessException(
                                    HttpStatus.BAD_REQUEST,
                                    "INVALID_PAYMENT_TYPE",
                                    "Hình thức thanh toán không hợp lệ");
                }

                boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());
                amountUsd =
                        isBookingVnd
                                ? bookingBaseAmount.divide(exchangeRate, 4, RoundingMode.HALF_UP)
                                : bookingBaseAmount;
                amountVnd =
                        isBookingVnd
                                ? bookingBaseAmount.setScale(0, RoundingMode.HALF_UP)
                                : bookingBaseAmount
                                        .multiply(exchangeRate)
                                        .setScale(0, RoundingMode.HALF_UP);
            }
        } else {
            // Thanh toán thẻ trực tiếp theo số tiền (không cần bookingId)
            if ("USD".equalsIgnoreCase(paymentCurrency)) {
                BigDecimal rawUsd =
                        request.getAmountUsd() != null
                                ? request.getAmountUsd()
                                : request.getAmount();
                if (rawUsd == null && request.getAmountVnd() != null) {
                    rawUsd = request.getAmountVnd().divide(exchangeRate, 2, RoundingMode.HALF_UP);
                }
                if (rawUsd == null || rawUsd.compareTo(new BigDecimal("0.01")) < 0) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_AMOUNT",
                            "Số tiền thanh toán thẻ bằng USD tối thiểu là 0.01 USD");
                }
                amountUsd = rawUsd.setScale(2, RoundingMode.HALF_UP);
                amountVnd =
                        request.getAmountVnd() != null
                                ? request.getAmountVnd().setScale(0, RoundingMode.HALF_UP)
                                : amountUsd
                                        .multiply(exchangeRate)
                                        .setScale(0, RoundingMode.HALF_UP);
            } else {
                BigDecimal rawVnd =
                        request.getAmountVnd() != null
                                ? request.getAmountVnd()
                                : request.getAmount();
                if (rawVnd == null && request.getAmountUsd() != null) {
                    rawVnd =
                            request.getAmountUsd()
                                    .multiply(exchangeRate)
                                    .setScale(0, RoundingMode.HALF_UP);
                }
                if (rawVnd == null || rawVnd.compareTo(BigDecimal.valueOf(2000)) < 0) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_AMOUNT",
                            "Số tiền thanh toán thẻ bằng VNĐ tối thiểu là 2.000 VNĐ");
                }
                amountVnd = rawVnd.setScale(0, RoundingMode.HALF_UP);
                amountUsd =
                        request.getAmountUsd() != null
                                ? request.getAmountUsd()
                                : amountVnd.divide(exchangeRate, 2, RoundingMode.HALF_UP);
            }
            bookingBaseAmount = amountUsd;
        }

        BigDecimal chargedAmount =
                "USD".equalsIgnoreCase(paymentCurrency)
                        ? amountUsd.setScale(2, RoundingMode.HALF_UP)
                        : amountVnd;

        BigDecimal surchargePercent;
        if (Boolean.TRUE.equals(request.getWaiveSurcharge())) {
            surchargePercent = BigDecimal.ZERO;
        } else if (request.getCustomSurchargePercent() != null) {
            surchargePercent = request.getCustomSurchargePercent();
        } else if (sepayProperties != null && sepayProperties.isCardSurchargeEnabled()) {
            BigDecimal resolved = sepayProperties.resolveCardSurcharge(cardBrand);
            surchargePercent = (resolved != null) ? resolved
                    : (sepayProperties.getCardSurchargePercent() != null ? sepayProperties.getCardSurchargePercent() : BigDecimal.ZERO);
        } else {
            surchargePercent = BigDecimal.ZERO;
        }
        BigDecimal surchargeAmount = chargedAmount.multiply(surchargePercent).divide(new BigDecimal("100"), "USD".equalsIgnoreCase(paymentCurrency) ? 2 : 0, RoundingMode.HALF_UP);
        BigDecimal totalAmountWithSurcharge = chargedAmount.add(surchargeAmount);

        String cleanRef =
                booking != null
                        ? booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "")
                        : "CARD" + (1000 + (int) (Math.random() * 9000));
        String stageSuffix =
                targetInstallment != null ? "S" + targetInstallment.getSequenceNo() : "F";
        String transferContent = "CASMS " + cleanRef + stageSuffix;

        String defaultIdempotencyKey =
                "SEPAY-"
                        + cardBrand
                        + "-"
                        + paymentCurrency
                        + "-"
                        + cleanRef
                        + "-"
                        + stageSuffix;
        String idempotencyKey =
                (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                        ? request.getIdempotencyKey().trim()
                        : defaultIdempotencyKey;

        Optional<Payment> existingPaymentOpt =
                paymentRepository.findByIdempotencyKey(idempotencyKey);
        Payment payment;
        if (existingPaymentOpt.isPresent()) {
            payment = existingPaymentOpt.get();
            if (payment.getStatus() == PaymentStatus.Confirmed) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "PAYMENT_ALREADY_CONFIRMED",
                        "Giao dịch thẻ này đã được xác nhận thành công trước đó!");
            }
        } else {
            payment = new Payment();
        }

        payment.setBooking(booking);
        payment.setInstallment(targetInstallment);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.Sepay);
        payment.setProvider("SEPAY_" + cardBrand);
        payment.setPaymentToken(resolvedToken);
        payment.setRequestedAmount(bookingBaseAmount);
        payment.setCurrency(
                booking != null && booking.getCurrency() != null
                        ? booking.getCurrency()
                        : paymentCurrency);
        payment.setOriginalAmount(chargedAmount);
        payment.setOriginalCurrency(paymentCurrency);
        payment.setConvertedAmount(chargedAmount);
        payment.setSettlementCurrency(paymentCurrency);
        payment.setExchangeRateApplied(exchangeRate);
        payment.setExchangeRateSource("SEPAY_CONFIG");
        payment.setExchangeRateTimestamp(Instant.now());
        payment.setRequestedAt(Instant.now());
        payment.setStatus(PaymentStatus.Pending);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setInternalReceiptReference(transferContent);
        payment = paymentRepository.save(payment);

        boolean hasCardCredentials =
                (request.getCardNumber() != null && !request.getCardNumber().isBlank())
                        || (request.getCardToken() != null && !request.getCardToken().isBlank());
        boolean shouldAutoCapture =
                request.getAutoCapture() != null ? request.getAutoCapture() : hasCardCredentials;

        String processorRef =
                "SEPAY-"
                        + cardBrand
                        + "-"
                        + paymentCurrency
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String checkoutUrl =
                String.format(
                        "%s?merchant=%s&paymentId=%s&brand=%s&currency=%s&amount=%s",
                        resolveCardGatewayUrl(),
                        resolveMerchantId(),
                        payment.getPaymentId(),
                        cardBrand,
                        paymentCurrency,
                        chargedAmount.toPlainString());

        if (shouldAutoCapture) {
            // Lưu lịch sử giao dịch cổng thẻ SePay (SepayTransaction)
            SepayTransaction tx = new SepayTransaction();
            tx.setSepayId(Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L) + 100_000_000L);
            tx.setGateway("SEPAY_" + cardBrand);
            tx.setAccountNumber(maskedCard);
            tx.setTransferType("in");
            tx.setTransferAmount(chargedAmount);
            tx.setCode(transferContent);
            tx.setContent(
                    transferContent
                            + " | "
                            + cardBrand
                            + " "
                            + maskedCard
                            + " | "
                            + chargedAmount
                            + " "
                            + paymentCurrency);
            tx.setReferenceCode(processorRef);
            tx.setDescription(stageDesc);
            tx.setPayment(payment);
            tx.setBooking(booking);
            tx.setTransactionDate(Instant.now());
            tx.setMatchedAt(Instant.now());
            tx.setStatus(SepayTransactionStatus.PROCESSED);
            sepayTransactionRepository.save(tx);

            if (booking != null && payment.getPaymentId() != null) {
                BigDecimal firstConfirmAmount =
                        targetInstallment != null
                                ? bookingBaseAmount.min(targetInstallment.getRequiredAmount())
                                : bookingBaseAmount;
                if (firstConfirmAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    firstConfirmAmount = bookingBaseAmount;
                }
                PaymentConfirmRequest confirmReq =
                        new PaymentConfirmRequest(firstConfirmAmount, processorRef);
                paymentService.confirmPayment(payment.getPaymentId(), confirmReq);
                payment.setStatus(PaymentStatus.Confirmed);
                payment.setConfirmedAmount(firstConfirmAmount);
                payment.setAuthorizedAmount(bookingBaseAmount);
                payment.setCapturedAmount(bookingBaseAmount);
                payment.setConfirmedAt(Instant.now());
                payment.setCapturedAt(Instant.now());
                payment.setProcessorReference(processorRef);

                // Phân bổ tiền thừa (surplus) nếu khách trả gộp nhiều đợt qua thẻ Visa/Mastercard
                BigDecimal surplusBase = bookingBaseAmount.subtract(firstConfirmAmount);
                if (surplusBase.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                    allocateSurplusToSubsequentInstallments(
                            booking,
                            surplusBase,
                            payment.getCurrency(),
                            processorRef,
                            payment.getProvider());
                }
            } else {
                payment.setStatus(PaymentStatus.Confirmed);
                payment.setConfirmedAmount(bookingBaseAmount);
                payment.setAuthorizedAmount(bookingBaseAmount);
                payment.setCapturedAmount(bookingBaseAmount);
                payment.setConfirmedAt(Instant.now());
                payment.setCapturedAt(Instant.now());
                payment.setProcessorReference(processorRef);
                payment = paymentRepository.save(payment);
            }
        }

        String instructions =
                payment.getStatus() == PaymentStatus.Confirmed
                        ? "Thanh toán thẻ quốc tế "
                                + cardBrand
                                + " ("
                                + maskedCard
                                + ") số tiền "
                                + chargedAmount
                                + " "
                                + paymentCurrency
                                + " qua SePay đã được uỷ quyền và xác nhận thành công (Mã GD: "
                                + processorRef
                                + ")."
                        : "Vui lòng hoàn tất xác thực 3D-Secure cho thẻ "
                                + cardBrand
                                + " với số tiền "
                                + chargedAmount
                                + " "
                                + paymentCurrency
                                + " tại cổng thanh toán SePay: "
                                + checkoutUrl;

        return SepayCheckoutResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking != null ? booking.getBookingId() : null)
                .bookingReference(booking != null ? booking.getBookingReference() : transferContent)
                .paymentType(effectivePaymentType)
                .installmentId(
                        targetInstallment != null ? targetInstallment.getInstallmentId() : null)
                .sequenceNo(targetInstallment != null ? targetInstallment.getSequenceNo() : null)
                .stageDescription(stageDesc)
                .amountUsd(amountUsd)
                .amountVnd(amountVnd)
                .exchangeRate(exchangeRate)
                .paymentCurrency(paymentCurrency)
                .chargedAmount(chargedAmount)
                .surchargePercent(surchargePercent)
                .surchargeAmount(surchargeAmount)
                .totalAmountWithSurcharge(totalAmountWithSurcharge)
                .paymentChannel(cardBrand)
                .cardBrand(cardBrand)
                .maskedCardNumber(maskedCard)
                .cardHolderName(request.getCardHolderName())
                .processorReference(
                        payment.getStatus() == PaymentStatus.Confirmed ? processorRef : null)
                .checkoutUrl(checkoutUrl)
                .supportedCurrencies(resolveSupportedCurrencies())
                .supportedCardBrands(resolveSupportedCardBrands())
                .bankName("SEPAY_" + cardBrand)
                .accountNumber(maskedCard)
                .accountHolderName(
                        request.getCardHolderName() != null
                                ? request.getCardHolderName()
                                : sepayProperties.getAccountName())
                .transferContent(transferContent)
                .status(payment.getStatus())
                .createdAt(payment.getRequestedAt())
                .instructions(instructions)
                .build();
    }

    /** Tiếp nhận và xử lý Webhook biến động số dư từ SePay (Idempotent). */
    @Transactional
    public SepayWebhookResponse processWebhook(SepayWebhookPayload payload, String authHeader) {
        log.info(
                "Received SePay webhook: id={}, amount={}, content={}",
                payload.getId(),
                payload.getTransferAmount(),
                payload.getContent());

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
            boolean valid = false;
            if (authHeader != null && !authHeader.isBlank()) {
                String token = authHeader.trim();
                if (token.regionMatches(true, 0, "Apikey ", 0, 7)) {
                    token = token.substring(7).trim();
                } else if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    token = token.substring(7).trim();
                }
                valid = java.security.MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8));
            }
            if (!valid) {
                log.error("Unauthorized SePay webhook attempt: invalid API key header");
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_SEPAY_API_KEY",
                        "Khóa xác thực SePay không hợp lệ");
            }
        }

        // 3. Bỏ qua các giao dịch không phải tiền vào ("in")
        if (payload.getTransferType() != null
                && !"in".equalsIgnoreCase(payload.getTransferType().trim())) {
            log.info("Ignored non-incoming transaction: {}", payload.getTransferType());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Ignored non-incoming transfer")
                    .build();
        }

        // 4. Kiểm tra trùng lặp giao dịch (Idempotent check qua SePay transaction ID)
        if (payload.getId() != null
                && sepayTransactionRepository.existsBySepayId(payload.getId())) {
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
                // Nếu khách CK chỉ ghi mã booking mà không tạo checkout trước: tìm pending payment
                // của booking đó
                List<Payment> pendingPayments =
                        paymentRepository
                                .findByBooking_BookingId(matchedBooking.getBookingId())
                                .stream()
                                .filter(
                                        p ->
                                                p.getStatus() == PaymentStatus.Pending
                                                        && p.getPaymentMethod()
                                                                == PaymentMethod.Sepay)
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

            String bookingRef =
                    matchedBooking != null
                            ? matchedBooking.getBookingReference()
                            : (matchedPayment.getInternalReceiptReference() != null
                                    ? matchedPayment.getInternalReceiptReference()
                                    : "N/A");

            BigDecimal exchangeRate = resolveDefaultExchangeRate();
            boolean isVndCurrency =
                    "VND".equalsIgnoreCase(matchedPayment.getCurrency())
                            || (matchedBooking != null
                                    && "VND".equalsIgnoreCase(matchedBooking.getCurrency()));
            String webhookCurrency =
                    normalizeSupportedCurrency(payload.getCurrency(), "VND");
            BigDecimal rawTransferAmount =
                    payload.getTransferAmount() != null
                            ? payload.getTransferAmount()
                            : BigDecimal.ZERO;

            BigDecimal expectedVnd =
                    isVndCurrency
                            ? matchedPayment.getRequestedAmount().setScale(0, RoundingMode.HALF_UP)
                            : matchedPayment
                                    .getRequestedAmount()
                                    .multiply(exchangeRate)
                                    .setScale(0, RoundingMode.HALF_UP);
            BigDecimal receivedVnd =
                    "USD".equalsIgnoreCase(webhookCurrency)
                            ? rawTransferAmount
                                    .multiply(exchangeRate)
                                    .setScale(0, RoundingMode.HALF_UP)
                            : rawTransferAmount;

            // Kiểm tra dung sai số tiền
            double diff = Math.abs(receivedVnd.subtract(expectedVnd).doubleValue());
            double maxAllowedDiff =
                    expectedVnd.doubleValue() * (sepayProperties.getTolerancePercent() / 100.0);

            if (receivedVnd.compareTo(expectedVnd) < 0 && diff > maxAllowedDiff) {
                log.warn(
                        "Transfer amount mismatch: received {} VND ({} {}), expected {} VND",
                        receivedVnd,
                        rawTransferAmount,
                        webhookCurrency,
                        expectedVnd);
                tx.setStatus(SepayTransactionStatus.AMOUNT_MISMATCH);
                sepayTransactionRepository.save(tx);
                return SepayWebhookResponse.builder()
                        .success(false)
                        .message(
                                "Transfer amount is insufficient. Received: "
                                        + receivedVnd
                                        + ", expected: "
                                        + expectedVnd)
                        .paymentId(matchedPayment.getPaymentId())
                        .bookingReference(bookingRef)
                        .build();
            }

            // Xác nhận thanh toán với cơ chế an toàn: không để exception rollback mất lịch sử giao
            // dịch SePay
            String processorRef = resolveProcessorReference(payload);
            try {
                PaymentConfirmRequest confirmReq =
                        new PaymentConfirmRequest(
                                matchedPayment.getRequestedAmount(), processorRef);
                paymentService.confirmPayment(matchedPayment.getPaymentId(), confirmReq);

                // --- XỬ LÝ THANH TOÁN GỘP & TIỀN THỪA (SURPLUS / BUNDLED ALLOCATION) ---
                int surplusStagesAllocated = 0;
                BigDecimal receivedInPaymentCurrency;
                if (isVndCurrency) {
                    receivedInPaymentCurrency = receivedVnd;
                } else if ("USD".equalsIgnoreCase(webhookCurrency)) {
                    receivedInPaymentCurrency = rawTransferAmount;
                } else {
                    receivedInPaymentCurrency =
                            receivedVnd.divide(exchangeRate, 4, RoundingMode.HALF_UP);
                }
                BigDecimal confirmedAmount = matchedPayment.getRequestedAmount();
                BigDecimal surplusUsd = receivedInPaymentCurrency.subtract(confirmedAmount);

                if (surplusUsd.compareTo(BigDecimal.valueOf(0.01)) > 0 && matchedBooking != null) {
                    List<Installment> installments =
                            installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                                    matchedBooking.getBookingId());
                    Map<UUID, BigDecimal> paidMap =
                            calculateWaterfallInstallmentPaidAmounts(
                                    matchedBooking.getBookingId(), installments);

                    for (Installment inst : installments) {
                        if (surplusUsd.compareTo(BigDecimal.valueOf(0.01)) <= 0) {
                            break;
                        }
                        BigDecimal paid =
                                paidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
                        BigDecimal needed = inst.getRequiredAmount().subtract(paid);
                        if (needed.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }
                        BigDecimal alloc = surplusUsd.min(needed);
                        if (alloc.compareTo(BigDecimal.ZERO) > 0) {
                            surplusStagesAllocated++;
                            String cleanRef =
                                    matchedBooking.getBookingReference().replaceAll("[^A-Za-z0-9]", "");
                            String surplusKey =
                                    "SEPAY-SURPLUS-"
                                            + (payload.getId() != null
                                                    ? payload.getId()
                                                    : UUID.randomUUID().toString())
                                            + "-S"
                                            + inst.getSequenceNo();

                            Payment surplusPayment = new Payment();
                            surplusPayment.setBooking(matchedBooking);
                            surplusPayment.setInstallment(inst);
                            surplusPayment.setOperationType(PaymentOperationType.Collect);
                            surplusPayment.setPaymentMethod(PaymentMethod.Sepay);
                            surplusPayment.setRequestedAmount(alloc);
                            surplusPayment.setConfirmedAmount(alloc);
                            surplusPayment.setCurrency(matchedPayment.getCurrency());
                            surplusPayment.setRequestedAt(Instant.now());
                            surplusPayment.setConfirmedAt(Instant.now());
                            surplusPayment.setStatus(PaymentStatus.Confirmed);
                            surplusPayment.setProcessorReference(processorRef + "-S" + inst.getSequenceNo());
                            surplusPayment.setInternalReceiptReference("CASMS " + cleanRef + "S" + inst.getSequenceNo());
                            surplusPayment.setIdempotencyKey(surplusKey);
                            paymentRepository.save(surplusPayment);

                            paidMap.put(inst.getInstallmentId(), paid.add(alloc));
                            surplusUsd = surplusUsd.subtract(alloc);
                            log.info(
                                    "Allocated surplus {} {} to installment {} (Seq {}) for booking {}",
                                    alloc,
                                    matchedPayment.getCurrency(),
                                    inst.getInstallmentId(),
                                    inst.getSequenceNo(),
                                    matchedBooking.getBookingReference());
                        }
                    }

                    if (surplusStagesAllocated > 0) {
                        paymentService.allocateInstallmentsAndSync(matchedBooking);
                    }

                    if (surplusUsd.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                        log.info(
                                "Booking {} has total overpayment surplus of {} {} beyond all installments",
                                matchedBooking.getBookingReference(),
                                surplusUsd,
                                matchedPayment.getCurrency());
                        tx.setDescription(
                                (tx.getDescription() != null ? tx.getDescription() + " | " : "")
                                        + "Khách chuyển dư: "
                                        + surplusUsd
                                        + " "
                                        + matchedPayment.getCurrency());
                    }
                }

                tx.setStatus(SepayTransactionStatus.PROCESSED);
                tx.setMatchedAt(Instant.now());
                sepayTransactionRepository.save(tx);

                String successMsg =
                        surplusStagesAllocated > 0
                                ? "Payment confirmed with surplus automatically allocated to "
                                        + surplusStagesAllocated
                                        + " subsequent stage(s)"
                                : "Payment confirmed successfully";

                log.info(
                        "Successfully confirmed payment {} for booking {}: {}",
                        matchedPayment.getPaymentId(),
                        bookingRef,
                        successMsg);
                return SepayWebhookResponse.builder()
                        .success(true)
                        .message(successMsg)
                        .paymentId(matchedPayment.getPaymentId())
                        .bookingReference(bookingRef)
                        .build();
            } catch (Exception e) {
                log.error(
                        "Error confirming payment {} for booking {}: {}",
                        matchedPayment.getPaymentId(),
                        bookingRef,
                        e.getMessage(),
                        e);
                tx.setStatus(SepayTransactionStatus.UNMATCHED);
                tx.setDescription(
                        (tx.getDescription() != null ? tx.getDescription() + " | " : "")
                                + "Confirm error: "
                                + e.getMessage());
                sepayTransactionRepository.save(tx);

                return SepayWebhookResponse.builder()
                        .success(true)
                        .message(
                                "SePay transaction recorded, but payment confirmation failed: "
                                        + e.getMessage())
                        .paymentId(matchedPayment.getPaymentId())
                        .bookingReference(bookingRef)
                        .build();
            }
        }

        // Trường hợp khách chuyển trực tiếp không có pending payment: tự động tạo payment mới cho
        // đợt tiếp theo
        String webhookCurrency = normalizeSupportedCurrency(payload.getCurrency(), "VND");
        BigDecimal rawTransferAmount =
                payload.getTransferAmount() != null ? payload.getTransferAmount() : BigDecimal.ZERO;
        BigDecimal exchangeRate = resolveDefaultExchangeRate();
        BigDecimal receivedVnd =
                "USD".equalsIgnoreCase(webhookCurrency)
                        ? rawTransferAmount.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP)
                        : rawTransferAmount;
        boolean isVndCurrency = "VND".equalsIgnoreCase(matchedBooking.getCurrency());
        BigDecimal receivedInBookingCurrency =
                isVndCurrency
                        ? receivedVnd
                        : ("USD".equalsIgnoreCase(webhookCurrency)
                                ? rawTransferAmount
                                : receivedVnd.divide(exchangeRate, 4, RoundingMode.HALF_UP));

        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                        matchedBooking.getBookingId());
        Installment nextUnpaid =
                findFirstUnpaidInstallment(matchedBooking.getBookingId(), installments);

        String autoIdempotencyKey =
                "SEPAY-AUTO-"
                        + (payload.getId() != null
                                ? payload.getId()
                                : UUID.randomUUID().toString());

        Payment newPayment = new Payment();
        newPayment.setBooking(matchedBooking);
        newPayment.setInstallment(nextUnpaid);
        newPayment.setOperationType(PaymentOperationType.Collect);
        newPayment.setPaymentMethod(PaymentMethod.Sepay);
        newPayment.setRequestedAmount(receivedInBookingCurrency);
        newPayment.setCurrency(
                matchedBooking.getCurrency() != null ? matchedBooking.getCurrency() : "USD");
        newPayment.setRequestedAt(Instant.now());
        newPayment.setStatus(PaymentStatus.Pending);
        newPayment.setIdempotencyKey(autoIdempotencyKey);
        newPayment.setInternalReceiptReference(content);
        newPayment = paymentRepository.save(newPayment);

        String processorRef = resolveProcessorReference(payload);
        try {
            paymentService.confirmPayment(
                    newPayment.getPaymentId(),
                    new PaymentConfirmRequest(receivedInBookingCurrency, processorRef));

            tx.setPayment(newPayment);
            tx.setStatus(SepayTransactionStatus.PROCESSED);
            tx.setMatchedAt(Instant.now());
            sepayTransactionRepository.save(tx);

            log.info(
                    "Created and confirmed automatic payment {} for booking {}",
                    newPayment.getPaymentId(),
                    matchedBooking.getBookingReference());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Automatic payment created and confirmed")
                    .paymentId(newPayment.getPaymentId())
                    .bookingReference(matchedBooking.getBookingReference())
                    .build();
        } catch (Exception e) {
            log.error(
                    "Error confirming auto payment {} for booking {}: {}",
                    newPayment.getPaymentId(),
                    matchedBooking.getBookingReference(),
                    e.getMessage(),
                    e);
            tx.setPayment(newPayment);
            tx.setStatus(SepayTransactionStatus.UNMATCHED);
            tx.setDescription(
                    (tx.getDescription() != null ? tx.getDescription() + " | " : "")
                            + "Auto-confirm error: "
                            + e.getMessage());
            sepayTransactionRepository.save(tx);

            return SepayWebhookResponse.builder()
                    .success(true)
                    .message(
                            "Automatic payment recorded, but confirmation failed: "
                                    + e.getMessage())
                    .paymentId(newPayment.getPaymentId())
                    .bookingReference(matchedBooking.getBookingReference())
                    .build();
        }
    }

    /** Tra cứu trạng thái thanh toán của một giao dịch theo paymentId. */
    @Transactional(readOnly = true)
    public PaymentStatusCheckResponse getPaymentStatus(
            UUID paymentId, UUID userId, boolean canReadAll) {
        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Payment not found with id: " + paymentId));

        if (payment.getBooking() != null) {
            assertAccess(payment.getBooking(), userId, canReadAll);
        }

        BigDecimal agreedFare =
                payment.getBooking() != null && payment.getBooking().getAgreedFareAmount() != null
                        ? payment.getBooking().getAgreedFareAmount()
                        : BigDecimal.ZERO;

        BigDecimal totalPaid =
                payment.getBooking() != null
                        ? paymentRepository
                                .findForBooking(payment.getBooking().getBookingId())
                                .stream()
                                .filter(
                                        p ->
                                                p.getStatus() == PaymentStatus.Confirmed
                                                        && p.getOperationType()
                                                                == PaymentOperationType.Collect
                                                        && p.getConfirmedAmount() != null)
                                .map(Payment::getConfirmedAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                        : BigDecimal.ZERO;

        BigDecimal remaining = agreedFare.subtract(totalPaid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        boolean isConfirmed =
                (payment.getBooking() != null
                                && payment.getBooking().getBookingStatus()
                                        == BookingStatus.Confirmed);

        return PaymentStatusCheckResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(
                        payment.getBooking() != null ? payment.getBooking().getBookingId() : null)
                .bookingReference(
                        payment.getBooking() != null
                                ? payment.getBooking().getBookingReference()
                                : payment.getInternalReceiptReference())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .requestedAmount(payment.getRequestedAmount())
                .confirmedAmount(payment.getConfirmedAmount())
                .currency(payment.getCurrency())
                .requestedAt(payment.getRequestedAt())
                .confirmedAt(payment.getConfirmedAt())
                .processorReference(payment.getProcessorReference())
                .bookingStatus(
                        payment.getBooking() != null
                                ? payment.getBooking().getBookingStatus()
                                : null)
                .isBookingConfirmed(isConfirmed)
                .remainingBookingBalance(remaining)
                .build();
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Phân bổ số tiền đã thanh toán (Confirmed) theo cơ chế tuần tự Waterfall cho các đợt thanh toán (Installments).
     * Bất kể khoản thanh toán được gắn nhãn đợt nào hoặc thanh toán gộp 100%, số tiền thu được sẽ ưu tiên
     * thanh toán đủ cho Đợt 1 (Cọc), sau đó đến các đợt tiếp theo theo thứ tự sequenceNo ASC.
     */
    public Map<UUID, BigDecimal> calculateWaterfallInstallmentPaidAmounts(
            UUID bookingId, List<Installment> installments) {
        Map<UUID, BigDecimal> allocated = new HashMap<>();
        if (installments == null || installments.isEmpty()) {
            return allocated;
        }

        BigDecimal totalConfirmedPaid =
                paymentRepository.findForBooking(bookingId).stream()
                        .filter(
                                p ->
                                        p.getStatus() == PaymentStatus.Confirmed
                                                && p.getOperationType() == PaymentOperationType.Collect
                                                && p.getConfirmedAmount() != null)
                        .map(Payment::getConfirmedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingToAllocate = totalConfirmedPaid;
        for (Installment inst : installments) {
            BigDecimal required =
                    inst.getRequiredAmount() != null ? inst.getRequiredAmount() : BigDecimal.ZERO;
            BigDecimal alloc = remainingToAllocate.min(required);
            if (alloc.compareTo(BigDecimal.ZERO) < 0) {
                alloc = BigDecimal.ZERO;
            }
            allocated.put(inst.getInstallmentId(), alloc);
            remainingToAllocate = remainingToAllocate.subtract(alloc);
            if (remainingToAllocate.compareTo(BigDecimal.ZERO) < 0) {
                remainingToAllocate = BigDecimal.ZERO;
            }
        }
        return allocated;
    }

    private BigDecimal getPaidAmountForInstallment(UUID bookingId, UUID installmentId) {
        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId);
        Map<UUID, BigDecimal> waterfall =
                calculateWaterfallInstallmentPaidAmounts(bookingId, installments);
        return waterfall.getOrDefault(installmentId, BigDecimal.ZERO);
    }

    private Installment findFirstUnpaidInstallment(UUID bookingId, List<Installment> installments) {
        Map<UUID, BigDecimal> paidMap =
                calculateWaterfallInstallmentPaidAmounts(bookingId, installments);
        for (Installment inst : installments) {
            BigDecimal paid = paidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            if (paid.compareTo(inst.getRequiredAmount()) < 0) {
                return inst;
            }
        }
        return null;
    }

    private Payment findMatchingPayment(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }

        // Truy vấn trực tiếp các payment đang Pending của SePay
        List<Payment> pendingPayments =
                paymentRepository.findByStatusAndPaymentMethod(
                        PaymentStatus.Pending, PaymentMethod.Sepay);

        if (pendingPayments.isEmpty()) {
            return null;
        }

        String cleanMemo = memo.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);

        // 1. Khớp theo internalReceiptReference (sắp xếp độ dài giảm dần để ưu tiên mã cụ thể hơn)
        List<Payment> sortedByReceipt =
                pendingPayments.stream()
                        .filter(
                                p ->
                                        p.getInternalReceiptReference() != null
                                                && !p.getInternalReceiptReference().isBlank())
                        .sorted(
                                Comparator.comparingInt(
                                                (Payment p) ->
                                                        p.getInternalReceiptReference().length())
                                        .reversed())
                        .toList();

        for (Payment p : sortedByReceipt) {
            String cleanReceipt =
                    p.getInternalReceiptReference()
                            .replaceAll("[^A-Za-z0-9]", "")
                            .toUpperCase(Locale.ROOT);
            if (!cleanReceipt.isBlank() && cleanMemo.contains(cleanReceipt)) {
                return p;
            }
        }

        // 2. Khớp theo idempotencyKey nếu có trong memo
        for (Payment p : pendingPayments) {
            if (p.getIdempotencyKey() != null && !p.getIdempotencyKey().isBlank()) {
                String cleanKey =
                        p.getIdempotencyKey()
                                .replaceAll("[^A-Za-z0-9]", "")
                                .toUpperCase(Locale.ROOT);
                if (!cleanKey.isBlank() && cleanMemo.contains(cleanKey)) {
                    return p;
                }
            }
        }

        return null;
    }

    private Booking findMatchingBooking(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }

        // Tìm các chuỗi dạng BK + số hoặc mã ký tự (ví dụ: BK-0001, BK0001, BK-2026-0001, BK20260001, BK-20261019-9B3E)
        Pattern pattern =
                Pattern.compile("BK[-_]?[A-Za-z0-9]{3,16}(?:[-_][A-Za-z0-9]{1,8})?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(memo);

        while (matcher.find()) {
            String candidate = matcher.group().toUpperCase(Locale.ROOT);

            // 1. Tìm trực tiếp theo nguyên bản
            Optional<Booking> directOpt = bookingRepository.findByBookingReference(candidate);
            if (directOpt.isPresent()) {
                return directOpt.get();
            }

            // 2. Chuẩn hóa nếu không có dấu gạch ngang (ví dụ: BK0001 -> BK-0001)
            String cleanDigits = candidate.replaceAll("[^0-9]", "");
            if (!cleanDigits.isBlank()) {
                // Định dạng chuẩn hiện tại của BusinessCodeGenerator: BK-XXXX (4 số)
                if (cleanDigits.length() == 4) {
                    Optional<Booking> stdOpt =
                            bookingRepository.findByBookingReference("BK-" + cleanDigits);
                    if (stdOpt.isPresent()) {
                        return stdOpt.get();
                    }
                }

                // Định dạng cũ: BK-YYYY-XXXX (>= 8 số)
                if (cleanDigits.length() >= 8) {
                    String legacyNormalized =
                            "BK-"
                                    + cleanDigits.substring(0, 4)
                                    + "-"
                                    + cleanDigits.substring(4);
                    Optional<Booking> normOpt =
                            bookingRepository.findByBookingReference(legacyNormalized);
                    if (normOpt.isPresent()) {
                        return normOpt.get();
                    }
                }
            }
        }

        return null;
    }

    private SepayTransaction recordSepayTransaction(SepayWebhookPayload payload) {
        SepayTransaction tx = new SepayTransaction();
        tx.setSepayId(payload.getId() != null ? payload.getId() : System.currentTimeMillis());
        String gateway = payload.getGateway();
        if ((gateway == null || gateway.isBlank()) && payload.getCardBrand() != null) {
            gateway = "SEPAY_" + payload.getCardBrand().trim().toUpperCase(Locale.ROOT);
        } else if ((gateway == null || gateway.isBlank()) && payload.getPaymentChannel() != null) {
            gateway = "SEPAY_" + payload.getPaymentChannel().trim().toUpperCase(Locale.ROOT);
        }
        tx.setGateway(gateway);
        tx.setAccountNumber(
                payload.getAccountNumber() != null
                        ? payload.getAccountNumber()
                        : payload.getMaskedCardNumber());
        tx.setTransferType(payload.getTransferType());
        tx.setTransferAmount(
                payload.getTransferAmount() != null
                        ? payload.getTransferAmount()
                        : BigDecimal.ZERO);
        tx.setAccumulated(payload.getAccumulated());
        tx.setCode(payload.getCode());
        tx.setContent(payload.getContent());
        tx.setReferenceCode(payload.getReferenceCode());
        tx.setDescription(payload.getDescription());
        tx.setStatus(SepayTransactionStatus.UNMATCHED);
        tx.setCreatedAt(Instant.now());

        if (payload.getTransactionDate() != null && !payload.getTransactionDate().isBlank()) {
            try {
                LocalDateTime ldt =
                        LocalDateTime.parse(
                                payload.getTransactionDate().trim(), SEPAY_DATE_FORMATTER);
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
        if (payload.getId() != null) {
            return "SEPAY-" + payload.getId();
        }
        if (payload.getReferenceCode() != null
                && !payload.getReferenceCode().isBlank()
                && !"0".equals(payload.getReferenceCode().trim())) {
            return payload.getReferenceCode().trim();
        }
        return "SEPAY-TX-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private BigDecimal resolveDefaultExchangeRate() {
        BigDecimal rate = sepayProperties.getUsdToVndRate();
        return (rate != null && rate.compareTo(BigDecimal.ZERO) > 0)
                ? rate
                : BigDecimal.valueOf(25400);
    }

    private String resolveDefaultCurrency() {
        String def = sepayProperties.getDefaultCurrency();
        return (def != null && !def.isBlank()) ? def.trim().toUpperCase(Locale.ROOT) : "VND";
    }

    private List<String> resolveSupportedCurrencies() {
        List<String> list = sepayProperties.getSupportedCurrencies();
        return (list != null && !list.isEmpty()) ? list : List.of("VND", "USD");
    }

    private List<String> resolveSupportedCardBrands() {
        List<String> list = sepayProperties.getSupportedCardBrands();
        return (list != null && !list.isEmpty()) ? list : List.of("VISA", "MASTERCARD");
    }

    private List<String> resolveSupportedPaymentChannels() {
        List<String> list = sepayProperties.getSupportedPaymentChannels();
        return (list != null && !list.isEmpty())
                ? list
                : List.of("VIETQR", "VISA", "MASTERCARD");
    }

    private String resolveMerchantId() {
        String id = sepayProperties.getMerchantId();
        return (id != null && !id.isBlank()) ? id.trim() : "CASMS-SEPAY-INTL-001";
    }

    private String resolveCardGatewayUrl() {
        String url = sepayProperties.getCardGatewayUrl();
        return (url != null && !url.isBlank())
                ? url.trim()
                : "https://pay.sepay.vn/v1/card-checkout";
    }

    private boolean isCardPaymentEnabled() {
        return !Boolean.FALSE.equals(sepayProperties.getCardEnabled());
    }

    private String normalizeSupportedCurrency(String rawCurrency, String fallback) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return fallback != null ? fallback.toUpperCase(Locale.ROOT) : "VND";
        }
        String rawUpper = rawCurrency.trim().toUpperCase(Locale.ROOT);
        final String cleaned = "VNĐ".equals(rawUpper) ? "VND" : rawUpper;
        List<String> supported = resolveSupportedCurrencies();
        boolean isAllowed =
                supported.stream().anyMatch(c -> c.equalsIgnoreCase(cleaned))
                        || "VND".equals(cleaned)
                        || "USD".equals(cleaned);
        if (!isAllowed) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_SEPAY_CURRENCY",
                    "SePay chỉ hỗ trợ thanh toán bằng Tiền Việt (VND) hoặc Đô la Mỹ (USD). Giá trị nhận được: "
                            + rawCurrency);
        }
        return cleaned;
    }

    private String resolvePaymentChannel(
            String rawChannel, String cardBrand, String cardNumber, String cardToken) {
        if ((rawChannel == null || rawChannel.isBlank())) {
            if ((cardBrand != null && !cardBrand.isBlank())
                    || (cardNumber != null && !cardNumber.isBlank())
                    || (cardToken != null && !cardToken.isBlank())) {
                return resolveAndValidateCardBrand(cardBrand, cardNumber, cardToken);
            }
            return "VIETQR";
        }
        String ch = rawChannel.trim().toUpperCase(Locale.ROOT);
        if ("VIETQR".equals(ch) || "QR".equals(ch) || "BANK".equals(ch) || "BANK_TRANSFER".equals(ch)) {
            return "VIETQR";
        }
        if ("CARD".equals(ch) || "VISA".equals(ch) || "MASTERCARD".equals(ch) || "MASTER".equals(ch) || "MC".equals(ch)) {
            String brandHint = "CARD".equals(ch) ? cardBrand : ch;
            return resolveAndValidateCardBrand(brandHint, cardNumber, cardToken);
        }
        throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_PAYMENT_CHANNEL",
                "Kênh thanh toán SePay không hợp lệ (chỉ hỗ trợ VIETQR, VISA, MASTERCARD): "
                        + rawChannel);
    }

    private String resolveAndValidateCardBrand(
            String requestedBrand, String cardNumber, String cardToken) {
        String detectedFromPan = null;
        if (cardNumber != null && !cardNumber.isBlank()) {
            String digits = cardNumber.replaceAll("[\\s-]", "");
            if (!digits.matches("\\d{13,19}")) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_CARD_NUMBER",
                        "Số thẻ quốc tế không hợp lệ (phải gồm 13-19 chữ số)");
            }
            if (digits.startsWith("4")) {
                detectedFromPan = "VISA";
            } else if (isMastercardBin(digits)) {
                detectedFromPan = "MASTERCARD";
            } else {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "UNSUPPORTED_CARD_BRAND",
                        "Số thẻ không thuộc dải BIN của VISA (4...) hoặc MASTERCARD (51-55, 2221-2720)");
            }
        }

        String normalizedRequested = null;
        if (requestedBrand != null && !requestedBrand.isBlank()) {
            String upper = requestedBrand.trim().toUpperCase(Locale.ROOT);
            if ("VISA".equals(upper) || "SEPAY_VISA".equals(upper)) {
                normalizedRequested = "VISA";
            } else if ("MASTERCARD".equals(upper)
                    || "MASTER".equals(upper)
                    || "MC".equals(upper)
                    || "SEPAY_MASTERCARD".equals(upper)) {
                normalizedRequested = "MASTERCARD";
            } else {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "UNSUPPORTED_CARD_BRAND",
                        "SePay chỉ hỗ trợ thẻ quốc tế VISA hoặc MASTERCARD. Giá trị nhận được: "
                                + requestedBrand);
            }
        }

        if (detectedFromPan != null
                && normalizedRequested != null
                && !detectedFromPan.equals(normalizedRequested)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "CARD_BRAND_MISMATCH",
                    "Số thẻ nhập vào là thẻ "
                            + detectedFromPan
                            + " nhưng thương hiệu thẻ chọn là "
                            + normalizedRequested);
        }

        if (detectedFromPan != null) {
            return detectedFromPan;
        }
        if (normalizedRequested != null) {
            return normalizedRequested;
        }
        if (cardToken != null && !cardToken.isBlank()) {
            String lowerTok = cardToken.toLowerCase(Locale.ROOT);
            if (lowerTok.contains("master") || lowerTok.contains("_mc_")) {
                return "MASTERCARD";
            }
            return "VISA";
        }
        return "VISA";
    }

    private boolean isMastercardBin(String digits) {
        if (digits.length() >= 2) {
            int prefix2 = Integer.parseInt(digits.substring(0, 2));
            if (prefix2 >= 51 && prefix2 <= 55) {
                return true;
            }
        }
        if (digits.length() >= 4) {
            int prefix4 = Integer.parseInt(digits.substring(0, 4));
            if (prefix4 >= 2221 && prefix4 <= 2720) {
                return true;
            }
        }
        return false;
    }

    private String maskCardNumber(String cardNumber, String cardToken, String cardBrand) {
        if (cardNumber != null && !cardNumber.isBlank()) {
            String digits = cardNumber.replaceAll("[\\s-]", "");
            if (digits.length() >= 4) {
                return "**** **** **** " + digits.substring(digits.length() - 4);
            }
        }
        if (cardToken != null && !cardToken.isBlank()) {
            String digits = cardToken.replaceAll("[^0-9]", "");
            if (digits.length() >= 4) {
                return "**** **** **** " + digits.substring(digits.length() - 4);
            }
        }
        return "MASTERCARD".equalsIgnoreCase(cardBrand)
                ? "**** **** **** 5412"
                : "**** **** **** 4242";
    }

    private String resolveCardToken(String cardToken, String cardNumber, String cardBrand) {
        if (cardToken != null && !cardToken.isBlank()) {
            return cardToken.trim();
        }
        String last4 = "4242";
        if (cardNumber != null && !cardNumber.isBlank()) {
            String digits = cardNumber.replaceAll("[\\s-]", "");
            if (digits.length() >= 4) {
                last4 = digits.substring(digits.length() - 4);
            }
        } else if ("MASTERCARD".equalsIgnoreCase(cardBrand)) {
            last4 = "5412";
        }
        return "tok_sepay_" + cardBrand.toLowerCase(Locale.ROOT) + "_" + last4;
    }

    private void allocateSurplusToSubsequentInstallments(
            Booking booking,
            BigDecimal surplusAmount,
            String currency,
            String processorRef,
            String provider) {
        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                        booking.getBookingId());
        Map<UUID, BigDecimal> paidMap =
                calculateWaterfallInstallmentPaidAmounts(booking.getBookingId(), installments);

        int surplusStagesAllocated = 0;
        BigDecimal remainingSurplus = surplusAmount;
        for (Installment inst : installments) {
            if (remainingSurplus.compareTo(BigDecimal.valueOf(0.01)) <= 0) {
                break;
            }
            BigDecimal paid = paidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal needed = inst.getRequiredAmount().subtract(paid);
            if (needed.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal alloc = remainingSurplus.min(needed);
            if (alloc.compareTo(BigDecimal.ZERO) > 0) {
                surplusStagesAllocated++;
                String cleanRef = booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "");
                String surplusKey =
                        "SEPAY-CARD-SURPLUS-"
                                + processorRef
                                + "-S"
                                + inst.getSequenceNo();

                Payment surplusPayment = new Payment();
                surplusPayment.setBooking(booking);
                surplusPayment.setInstallment(inst);
                surplusPayment.setOperationType(PaymentOperationType.Collect);
                surplusPayment.setPaymentMethod(PaymentMethod.Sepay);
                surplusPayment.setProvider(provider);
                surplusPayment.setRequestedAmount(alloc);
                surplusPayment.setConfirmedAmount(alloc);
                surplusPayment.setAuthorizedAmount(alloc);
                surplusPayment.setCapturedAmount(alloc);
                surplusPayment.setCurrency(currency);
                surplusPayment.setRequestedAt(Instant.now());
                surplusPayment.setConfirmedAt(Instant.now());
                surplusPayment.setCapturedAt(Instant.now());
                surplusPayment.setStatus(PaymentStatus.Confirmed);
                surplusPayment.setProcessorReference(processorRef + "-S" + inst.getSequenceNo());
                surplusPayment.setInternalReceiptReference(
                        "CASMS " + cleanRef + "S" + inst.getSequenceNo());
                surplusPayment.setIdempotencyKey(surplusKey);
                paymentRepository.save(surplusPayment);

                paidMap.put(inst.getInstallmentId(), paid.add(alloc));
                remainingSurplus = remainingSurplus.subtract(alloc);
            }
        }
        if (surplusStagesAllocated > 0) {
            paymentService.allocateInstallmentsAndSync(booking);
        }
    }

    private void assertAccess(Booking booking, UUID userId, boolean canReadAll) {
        if (canReadAll) {
            return;
        }
        if (booking != null && booking.getBookedByUser() != null) {
            if (userId == null || !userId.equals(booking.getBookedByUser().getUserId())) {
                throw new AccessDeniedException(
                        "Bạn chỉ có thể xem hoặc thực hiện thanh toán cho đơn của chính mình");
            }
        }
    }
}
