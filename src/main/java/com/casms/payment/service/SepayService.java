package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingContract;
import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.booking.repository.BookingContractRepository;
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
import com.casms.payment.entity.Refund;
import com.casms.payment.entity.SepayTransaction;
import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.PaymentType;
import com.casms.payment.enums.SepayTransactionStatus;
import com.casms.payment.repository.InstallmentRepository;
import com.casms.payment.repository.PaymentRepository;
import com.casms.payment.repository.RefundRepository;
import com.casms.payment.repository.SepayTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final RefundRepository refundRepository;
    private final BookingContractRepository bookingContractRepository;
    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final SepayWebhookIngestionService webhookIngestionService;

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

        Instant now = Instant.now();
        BigDecimal agreedFare =
                booking.getAgreedFareAmount() != null
                        ? booking.getAgreedFareAmount()
                        : BigDecimal.ZERO;
        BigDecimal exchangeRate = resolveDefaultExchangeRate();
        boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());

        BigDecimal totalPaid = calculateVerifiedNetPaidReadOnly(booking);
        BigDecimal outstanding = BookingPaymentBalanceHelper.outstandingBalance(booking, totalPaid);
        boolean fullyPaid = outstanding.signum() == 0;
        boolean holdExpired = BookingPaymentBalanceHelper.isHoldExpired(booking, now);
        boolean isCancelled = booking.getBookingStatus() == BookingStatus.Cancelled;
        boolean canCollect = !isCancelled && !holdExpired && !fullyPaid;

        BookingContractStatus contractStatus = resolveContractStatus(bookingId);

        String ineligibleCode = null;
        String ineligibleReason = null;
        if (isCancelled) {
            ineligibleCode = "BOOKING_CANCELLED";
            ineligibleReason = "Đặt chỗ đã bị hủy nên không thể tạo lệnh thanh toán.";
        } else if (holdExpired) {
            ineligibleCode = "BOOKING_HOLD_EXPIRED";
            ineligibleReason =
                    "Thời gian giữ chỗ của đặt chỗ đã hết hạn. Vui lòng liên hệ hỗ trợ hoặc tạo"
                            + " đặt chỗ mới.";
        } else if (fullyPaid) {
            ineligibleCode = "BOOKING_ALREADY_PAID";
            ineligibleReason = "Đặt chỗ này đã được thanh toán đủ.";
        } else if (!sepayProperties.isEnabled()) {
            ineligibleCode = "SEPAY_DISABLED";
            ineligibleReason = "Cổng thanh toán VietQR hiện đang tạm ngưng.";
        } else if (!isBookingVnd) {
            ineligibleCode = "NON_VND_BOOKING";
            ineligibleReason =
                    "Thanh toán VietQR trực tuyến hiện áp dụng cho đặt chỗ có đơn vị tiền tệ VND.";
        }
        boolean vietQrEligible = canCollect && sepayProperties.isEnabled() && isBookingVnd;

        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(bookingId);

        BigDecimal fullUsd = toUsdAmount(outstanding, isBookingVnd, exchangeRate);
        BigDecimal fullVnd = toVndAmount(outstanding, isBookingVnd, exchangeRate);
        Instant fullDueAt =
                installments.size() == 1
                        ? installments.get(0).getDueAt()
                        : booking.getFinalPaymentDueAt();

        // 1. Tuỳ chọn FULL: Thanh toán toàn bộ số tiền còn lại
        BookingPaymentOptionsResponse.FullPaymentOption fullOption =
                BookingPaymentOptionsResponse.FullPaymentOption.builder()
                        .isAvailable(canCollect)
                        .amountUsd(fullUsd)
                        .amountVnd(fullVnd)
                        .dueAt(fullDueAt)
                        .description(
                                "Thanh toán toàn bộ 100% số tiền còn lại ("
                                        + outstanding
                                        + " "
                                        + booking.getCurrency()
                                        + ")")
                        .build();

        Map<UUID, BigDecimal> installmentPaidMap =
                BookingPaymentBalanceHelper.allocateWaterfall(totalPaid, installments);

        // 2. Tuỳ chọn DEPOSIT: Đặt cọc (Đợt 1)
        BookingPaymentOptionsResponse.DepositPaymentOption depositOption = null;
        if (!installments.isEmpty()) {
            Installment inst1 = installments.get(0);
            BigDecimal paidForInst1 =
                    installmentPaidMap.getOrDefault(inst1.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal remInst1 =
                    inst1.getStatus() == InstallmentStatus.Cancelled || fullyPaid
                            ? BigDecimal.ZERO
                            : inst1.getRequiredAmount().subtract(paidForInst1).max(BigDecimal.ZERO);
            boolean isDepositPaid =
                    inst1.getStatus() != InstallmentStatus.Cancelled && remInst1.signum() == 0;
            boolean isDepositAvail =
                    canCollect && !isDepositPaid && inst1.getStatus() != InstallmentStatus.Cancelled;

            depositOption =
                    BookingPaymentOptionsResponse.DepositPaymentOption.builder()
                            .isAvailable(isDepositAvail)
                            .isPaid(isDepositPaid)
                            .installmentId(inst1.getInstallmentId())
                            .sequenceNo(inst1.getSequenceNo())
                            .requiredAmountUsd(
                                    toUsdAmount(inst1.getRequiredAmount(), isBookingVnd, exchangeRate))
                            .requiredAmountVnd(
                                    toVndAmount(inst1.getRequiredAmount(), isBookingVnd, exchangeRate))
                            .paidAmountUsd(toUsdAmount(paidForInst1, isBookingVnd, exchangeRate))
                            .paidAmountVnd(toVndAmount(paidForInst1, isBookingVnd, exchangeRate))
                            .remainingAmountUsd(toUsdAmount(remInst1, isBookingVnd, exchangeRate))
                            .remainingAmountVnd(toVndAmount(remInst1, isBookingVnd, exchangeRate))
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
        BookingPaymentOptionsResponse.StageDetail currentDueStage = null;
        boolean foundCurrentDue = false;
        for (Installment inst : installments) {
            boolean instCancelled = inst.getStatus() == InstallmentStatus.Cancelled;
            BigDecimal paidForInst =
                    installmentPaidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            BigDecimal remInst =
                    instCancelled || fullyPaid
                            ? BigDecimal.ZERO
                            : inst.getRequiredAmount().subtract(paidForInst).max(BigDecimal.ZERO);
            boolean isPaid = !instCancelled && remInst.signum() == 0;
            boolean isCurrentDue = false;
            if (!instCancelled && !isPaid && !foundCurrentDue && canCollect) {
                isCurrentDue = true;
                foundCurrentDue = true;
            }

            String desc = resolveStageDescription(installments.size(), inst);
            BookingPaymentOptionsResponse.StageDetail stageDetail =
                    BookingPaymentOptionsResponse.StageDetail.builder()
                            .installmentId(inst.getInstallmentId())
                            .sequenceNo(inst.getSequenceNo())
                            .installmentType(inst.getInstallmentType().name())
                            .requiredAmountUsd(
                                    toUsdAmount(inst.getRequiredAmount(), isBookingVnd, exchangeRate))
                            .requiredAmountVnd(
                                    toVndAmount(inst.getRequiredAmount(), isBookingVnd, exchangeRate))
                            .paidAmountUsd(toUsdAmount(paidForInst, isBookingVnd, exchangeRate))
                            .paidAmountVnd(toVndAmount(paidForInst, isBookingVnd, exchangeRate))
                            .remainingAmountUsd(toUsdAmount(remInst, isBookingVnd, exchangeRate))
                            .remainingAmountVnd(toVndAmount(remInst, isBookingVnd, exchangeRate))
                            .dueAt(inst.getDueAt())
                            .status(inst.getStatus().name())
                            .isPaid(isPaid)
                            .isCurrentDue(isCurrentDue)
                            .description(desc)
                            .build();
            stageDetails.add(stageDetail);
            if (isCurrentDue) {
                currentDueStage = stageDetail;
            }
        }

        BookingPaymentOptionsResponse.StagePaymentOption stageOption =
                BookingPaymentOptionsResponse.StagePaymentOption.builder()
                        .isAvailable(canCollect && currentDueStage != null)
                        .totalStages(installments.size())
                        .stages(stageDetails)
                        .build();

        PaymentPlan plan = resolvePaymentPlan(installments);
        BookingPaymentOptionsResponse.RecommendedPaymentOption recommended =
                buildRecommendedOption(
                        booking,
                        plan,
                        canCollect,
                        outstanding,
                        fullVnd,
                        fullDueAt,
                        depositOption,
                        currentDueStage);

        SepayCheckoutResponse activePendingOrder =
                findActivePendingVietQrResponse(
                        booking,
                        contractStatus,
                        installments,
                        installmentPaidMap,
                        outstanding,
                        canCollect,
                        exchangeRate);

        return BookingPaymentOptionsResponse.builder()
                .bookingId(bookingId)
                .bookingReference(booking.getBookingReference())
                .bookingStatus(booking.getBookingStatus())
                .contractStatus(contractStatus)
                .holdExpiresAt(booking.getHoldExpiresAt())
                .currency(booking.getCurrency())
                .agreedFareAmount(toUsdAmount(agreedFare, isBookingVnd, exchangeRate))
                .agreedFareAmountVnd(toVndAmount(agreedFare, isBookingVnd, exchangeRate))
                .totalPaidAmount(toUsdAmount(totalPaid, isBookingVnd, exchangeRate))
                .totalPaidAmountVnd(toVndAmount(totalPaid, isBookingVnd, exchangeRate))
                .outstandingBalance(fullUsd)
                .outstandingBalanceVnd(fullVnd)
                .fullyPaid(fullyPaid)
                .exchangeRate(exchangeRate)
                .vietQrEligible(vietQrEligible)
                .ineligibleCode(ineligibleCode)
                .ineligibleReason(ineligibleReason)
                .recommendedOption(recommended)
                .activePendingOrder(activePendingOrder)
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
     * <p>Thiết kế Idempotent và cố định thông tin lệnh Pending đã tạo để tránh lệch số tiền/mã QR
     * khi tải lại trang hoặc bấm lặp.
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
                        .findByIdForUpdate(request.getBookingId())
                        .or(() -> bookingRepository.findById(request.getBookingId()))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Booking not found with id: "
                                                        + request.getBookingId()));

        assertAccess(booking, userId, canReadAll);

        Instant now = Instant.now();
        if (booking.getBookingStatus() == BookingStatus.Cancelled) {
            throw new BusinessException(
                    "BOOKING_CANCELLED", "Không thể tạo thanh toán cho đơn đặt chỗ đã bị hủy");
        }
        if (BookingPaymentBalanceHelper.isHoldExpired(booking, now)) {
            throw new BusinessException(
                    "BOOKING_HOLD_EXPIRED",
                    "Thời gian giữ chỗ của đơn đã hết hạn. Vui lòng tạo đơn mới.");
        }
        if (!sepayProperties.isEnabled()) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEPAY_DISABLED",
                    "Cổng thanh toán VietQR hiện đang tạm ngưng.");
        }

        // PAY-03: Server-authoritative exchange rate only; never trust client customExchangeRate.
        BigDecimal exchangeRate = resolveDefaultExchangeRate();
        String paymentCurrency =
                normalizeSupportedCurrency(request.getCurrency(), resolveDefaultCurrency());
        String paymentChannel =
                resolvePaymentChannel(
                        request.getPaymentChannel(),
                        request.getCardBrand(),
                        request.getCardNumber(),
                        request.getCardToken());
        if ("VIETQR".equalsIgnoreCase(paymentChannel)
                && !"VND".equalsIgnoreCase(booking.getCurrency())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "NON_VND_BOOKING",
                    "Thanh toán VietQR trực tuyến hiện áp dụng cho đặt chỗ có đơn vị tiền tệ VND.");
        }

        BigDecimal totalPaid = calculateVerifiedNetPaidReadOnly(booking);
        BigDecimal outstanding = BookingPaymentBalanceHelper.outstandingBalance(booking, totalPaid);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "BOOKING_ALREADY_PAID",
                    "Đơn đặt chỗ này đã được thanh toán toàn bộ!");
        }

        List<Installment> installments =
                installmentRepository.findByBookingBookingIdOrderBySequenceNoAsc(
                        booking.getBookingId());
        Map<UUID, BigDecimal> installmentPaidMap =
                BookingPaymentBalanceHelper.allocateWaterfall(totalPaid, installments);

        BigDecimal bookingBaseAmount;
        Installment targetInstallment = null;
        String stageDesc;

        switch (request.getPaymentType()) {
            case FULL -> {
                bookingBaseAmount = outstanding;
                targetInstallment =
                        findFirstUnpaidInstallment(installments, installmentPaidMap);
                stageDesc = "Thanh toán toàn bộ 100% số dư còn lại";
            }
            case DEPOSIT -> {
                if (installments.isEmpty()) {
                    bookingBaseAmount =
                            booking.getRequiredDepositAmount() != null
                                    ? booking.getRequiredDepositAmount().min(outstanding)
                                    : outstanding;
                    stageDesc = "Thanh toán đặt cọc";
                } else {
                    Installment inst1 = installments.get(0);
                    if (inst1.getStatus() == InstallmentStatus.Cancelled) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "DEPOSIT_UNAVAILABLE",
                                "Đợt đặt cọc không còn hiệu lực.");
                    }
                    BigDecimal paidForInst1 =
                            installmentPaidMap.getOrDefault(
                                    inst1.getInstallmentId(), BigDecimal.ZERO);
                    BigDecimal remainingForDeposit =
                            inst1.getRequiredAmount().subtract(paidForInst1).min(outstanding);
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
                                findFirstUnpaidInstallment(installments, installmentPaidMap);
                        if (targetInstallment == null) {
                            throw new BusinessException(
                                    HttpStatus.BAD_REQUEST,
                                    "ALL_STAGES_PAID",
                                    "Tất cả các đợt thanh toán của đơn đặt tour này đã hoàn tất!");
                        }
                    }

                    if (targetInstallment.getStatus() == InstallmentStatus.Cancelled) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "STAGE_CANCELLED",
                                "Đợt thanh toán đã bị hủy.");
                    }
                    BigDecimal paidForTarget =
                            installmentPaidMap.getOrDefault(
                                    targetInstallment.getInstallmentId(), BigDecimal.ZERO);
                    BigDecimal remainingForStage =
                            targetInstallment
                                    .getRequiredAmount()
                                    .subtract(paidForTarget)
                                    .min(outstanding);
                    if (remainingForStage.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "STAGE_ALREADY_PAID",
                                "Đợt thanh toán số "
                                        + targetInstallment.getSequenceNo()
                                        + " đã được thanh toán đầy đủ!");
                    }
                    bookingBaseAmount = remainingForStage;
                    stageDesc = resolveStageDescription(installments.size(), targetInstallment);
                }
            }
            default ->
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_PAYMENT_TYPE",
                            "Hình thức thanh toán không hợp lệ");
        }

        boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());
        BigDecimal amountUsd = toUsdAmount(bookingBaseAmount, isBookingVnd, exchangeRate);
        BigDecimal amountVnd = toVndAmount(bookingBaseAmount, isBookingVnd, exchangeRate);

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

        // Xác thực số dư chưa bị thay đổi kể từ lúc khách mở trang (BE-01)
        BigDecimal expectedOutstanding = request.getExpectedOutstandingBalance();
        if (expectedOutstanding != null) {
            boolean matchesBookingCurrency = expectedOutstanding.compareTo(outstanding) == 0;
            boolean matchesVndOptionsUsd =
                    isBookingVnd
                            && expectedOutstanding.compareTo(
                                            toUsdAmount(outstanding, true, exchangeRate))
                                    == 0;
            if (!matchesBookingCurrency && !matchesVndOptionsUsd) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "PAYMENT_BALANCE_CHANGED",
                        "Số dư đặt chỗ đã thay đổi kể từ lúc tải trang. Vui lòng tải lại thông tin"
                                + " thanh toán trước khi tạo mã VietQR.");
            }
        }
        if (request.getExpectedAmountVnd() != null
                && request.getExpectedAmountVnd()
                                .setScale(0, RoundingMode.HALF_UP)
                                .compareTo(amountVnd)
                        != 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_BALANCE_CHANGED",
                    "Số tiền khoản thanh toán đã thay đổi. Vui lòng tải lại thông tin đặt chỗ"
                            + " trước khi tạo mã VietQR.");
        }

        BigDecimal chargedAmount = "USD".equalsIgnoreCase(paymentCurrency) ? amountUsd : amountVnd;
        String cleanRef = booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "");
        String scopeSuffix = resolveScopeSuffix(request.getPaymentType(), targetInstallment);
        String defaultIdempotencyKey =
                "SEPAY-" + cleanRef + "-" + request.getPaymentType().name() + "-" + scopeSuffix;
        String idempotencyKey =
                (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                        ? request.getIdempotencyKey().trim()
                        : defaultIdempotencyKey;

        BookingContractStatus contractStatus = resolveContractStatus(booking.getBookingId());

        // 1. Idempotency check (BE-02): cùng khóa + cùng phạm vi + cùng lựa chọn -> trả lại lệnh gốc không sửa đổi
        Optional<Payment> existingByKeyOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKeyOpt.isPresent()) {
            Payment existing = existingByKeyOpt.get();
            assertIdempotencyScopeMatches(
                    existing,
                    booking.getBookingId(),
                    request.getPaymentType(),
                    targetInstallment != null ? targetInstallment.getInstallmentId() : null,
                    paymentCurrency);
            if (existing.getStatus() == PaymentStatus.Confirmed) {
                return buildVietQrResponseFromPayment(
                        existing,
                        booking,
                        contractStatus,
                        request.getPaymentType(),
                        stageDesc,
                        exchangeRate,
                        false);
            }
            if (existing.getStatus() == PaymentStatus.Pending) {
                if (existing.getRequestedAmount() != null
                        && existing.getRequestedAmount().compareTo(bookingBaseAmount) == 0) {
                    return buildVietQrResponseFromPayment(
                            existing,
                            booking,
                            contractStatus,
                            request.getPaymentType(),
                            stageDesc,
                            exchangeRate,
                            false);
                }
                // Số dư thay đổi so với lệnh pending cũ -> đánh dấu superseded và yêu cầu FE tải lại
                existing.setStatus(PaymentStatus.Cancelled);
                existing.setFailureReason("SUPERSEDED_BALANCE_CHANGED");
                paymentRepository.save(existing);
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "PAYMENT_BALANCE_CHANGED",
                        "Số dư đặt chỗ đã thay đổi so với lệnh QR trước đó. Vui lòng tải lại trang"
                                + " để tạo lệnh mới với số tiền chính xác.");
            }
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_ORDER_CLOSED",
                    "Lệnh thanh toán trước đã hết hiệu lực. Vui lòng tạo yêu cầu mới.");
        }

        // 2. Kiểm tra lệnh VietQR Pending hiện hành của đặt chỗ (tránh sinh 2 lệnh hoạt động song song)
        List<Payment> activePendingList = findActivePendingVietQrPayments(booking.getBookingId());
        if (!activePendingList.isEmpty()) {
            Payment existingPending = activePendingList.get(0);
            boolean sameOption =
                    isSameCheckoutOption(
                            existingPending,
                            request.getPaymentType(),
                            targetInstallment != null ? targetInstallment.getInstallmentId() : null,
                            paymentCurrency);
            boolean sameAmount =
                    existingPending.getRequestedAmount() != null
                            && existingPending.getRequestedAmount().compareTo(bookingBaseAmount) == 0;

            if (sameOption && sameAmount) {
                // Trả lại nguyên trạng lệnh Pending hiện có, KHÔNG ghi đè thông tin QR/số tiền/mã chuyển khoản
                return buildVietQrResponseFromPayment(
                        existingPending,
                        booking,
                        contractStatus,
                        request.getPaymentType(),
                        stageDesc,
                        exchangeRate,
                        false);
            }

            // Đổi khoản thanh toán hoặc số dư đã thay đổi: đóng lệnh cũ nhưng giữ nguyên mã chuyển khoản cũ để đối soát khoản đến sau
            for (Payment stalePending : activePendingList) {
                stalePending.setStatus(PaymentStatus.Cancelled);
                stalePending.setFailureReason(
                        sameOption ? "SUPERSEDED_BALANCE_CHANGED" : "SUPERSEDED_OPTION_CHANGED");
                paymentRepository.save(stalePending);
            }
            paymentRepository.flush();
        }

        // 3. Tạo lệnh VietQR mới với mã chuyển khoản duy nhất và thông tin QR cố định
        String transferContent =
                generateUniqueTransferContent(
                        booking.getBookingId(), cleanRef, request.getPaymentType(), targetInstallment);
        String bankName = sepayProperties.getBankName();
        String accountNumber = sepayProperties.getAccountNumber();
        String accountName = sepayProperties.getAccountName();
        String qrUrl = buildVietQrUrl(accountNumber, bankName, amountVnd, transferContent);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setInstallment(targetInstallment);
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.Sepay);
        payment.setProvider("SEPAY_VIETQR");
        payment.setCheckoutPaymentType(request.getPaymentType());
        payment.setRequestedAmount(bookingBaseAmount);
        payment.setCurrency(booking.getCurrency());
        payment.setOriginalAmount(chargedAmount);
        payment.setOriginalCurrency(paymentCurrency);
        payment.setConvertedAmount(chargedAmount);
        payment.setSettlementCurrency(paymentCurrency);
        payment.setExchangeRateApplied(exchangeRate);
        payment.setExchangeRateSource("SEPAY_CONFIG");
        payment.setExchangeRateTimestamp(now);
        payment.setRequestedAt(now);
        payment.setStatus(PaymentStatus.Pending);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setInternalReceiptReference(transferContent);
        payment.setQrCodeUrl(qrUrl);
        payment.setQrBankName(bankName);
        payment.setQrAccountNumber(accountNumber);
        payment.setQrAccountHolderName(accountName);

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException race) {
            log.warn(
                    "Concurrent SePay checkout race detected for booking {} key={}",
                    booking.getBookingId(),
                    idempotencyKey);
            Optional<Payment> racedByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (racedByKey.isPresent()) {
                return buildVietQrResponseFromPayment(
                        racedByKey.get(),
                        booking,
                        contractStatus,
                        request.getPaymentType(),
                        stageDesc,
                        exchangeRate,
                        false);
            }
            List<Payment> racedPending = findActivePendingVietQrPayments(booking.getBookingId());
            if (!racedPending.isEmpty()) {
                return buildVietQrResponseFromPayment(
                        racedPending.get(0),
                        booking,
                        contractStatus,
                        request.getPaymentType(),
                        stageDesc,
                        exchangeRate,
                        false);
            }
            throw race;
        }

        return buildVietQrResponseFromPayment(
                payment,
                booking,
                contractStatus,
                request.getPaymentType(),
                stageDesc,
                exchangeRate,
                false);
    }

    /**
     * Tạo yêu cầu thanh toán nhanh theo số tiền (Quick Checkout) hỗ trợ cả VND (Tiền Việt) và USD,
     * cũng như kênh VietQR hoặc thẻ quốc tế Visa / Mastercard.
     */
    @Transactional
    public SepayCheckoutResponse createQuickCheckout(SepayQuickCheckoutRequest request) {
        return createQuickCheckout(request, null, false);
    }

    public SepayCheckoutResponse createQuickCheckout(
            SepayQuickCheckoutRequest request, UUID userId, boolean canReadAll) {
        if (!sepayProperties.isEnabled()) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEPAY_DISABLED",
                    "Cổng thanh toán SePay hiện đang tạm ngưng.");
        }

        // PAY-02: Require authentication; non-staff must bind payment to an owned booking (no orphan payments).
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cần đăng nhập để tạo thanh toán SePay.");
        }
        Booking linkedBooking = null;
        if (request.getBookingId() != null) {
            linkedBooking =
                    bookingRepository
                            .findById(request.getBookingId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Booking not found with id: "
                                                            + request.getBookingId()));
            assertAccess(linkedBooking, userId, canReadAll);
        } else if (!canReadAll) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "BOOKING_ID_REQUIRED",
                    "Vui lòng chọn đơn đặt chỗ hợp lệ trước khi tạo thanh toán.");
        }

        // PAY-03: Server-authoritative exchange rate only.
        BigDecimal exchangeRate = resolveDefaultExchangeRate();

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
            // Always recompute VND server-side from authoritative FX rate (never trust dual client amounts).
            amountVnd = amountUsd.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
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
            // Always recompute USD server-side from authoritative FX rate.
            amountUsd = amountVnd.divide(exchangeRate, 2, RoundingMode.HALF_UP);
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
                            .autoCapture(false)
                            .build();
            return createCardCheckout(cardReq, userId, canReadAll);
        }

        log.info(
                "Creating SePay quick checkout: currency={}, amountVnd={}, amountUsd={}",
                paymentCurrency,
                amountVnd,
                amountUsd);

        Booking booking = linkedBooking;

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

        // PAY-03: Server-authoritative exchange rate only; never trust client customExchangeRate.
        BigDecimal exchangeRate = resolveDefaultExchangeRate();

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
        if (canReadAll && Boolean.TRUE.equals(request.getWaiveSurcharge())) {
            surchargePercent = BigDecimal.ZERO;
        } else if (canReadAll && request.getCustomSurchargePercent() != null) {
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

        // PAY-01: Never self-confirm card payments from client request/autoCapture without verified provider capture.
        boolean shouldAutoCapture = false;

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

    /**
     * Tiếp nhận và xử lý Webhook biến động số dư từ SePay (hai pha giao dịch độc lập: lưu sự kiện
     * trước, khóa và ghi sổ chính xác sau; mọi ngoại lệ đi vào đối soát Finance).
     */
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

        // 2. Xác thực API Key fail-closed (từ chối nếu chưa cấu hình khóa hoặc khóa không khớp)
        if (sepayProperties.getApiKey() == null || sepayProperties.getApiKey().isBlank()) {
            log.error("SePay webhook rejected: SEPAY_API_KEY is not configured");
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "SEPAY_API_KEY_NOT_CONFIGURED",
                    "Chưa cấu hình khóa xác thực SePay");
        }
        String expected = sepayProperties.getApiKey().trim();
        boolean valid = false;
        if (authHeader != null && !authHeader.isBlank()) {
            String token = authHeader.trim();
            if (token.regionMatches(true, 0, "Apikey ", 0, 7)) {
                token = token.substring(7).trim();
            } else if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            }
            valid =
                    java.security.MessageDigest.isEqual(
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

        // 3. Bỏ qua nhanh giao dịch chuyển tiền đi (transferType != 'in') nếu có giá trị rõ ràng
        if (payload.getTransferType() != null
                && !payload.getTransferType().isBlank()
                && !"in".equalsIgnoreCase(payload.getTransferType().trim())) {
            log.info("Ignored non-incoming transaction: {}", payload.getTransferType());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Ignored non-incoming transfer")
                    .build();
        }

        // 4. Pha 1 (REQUIRES_NEW): Lưu sự kiện theo unique sepay_id (chống trùng lặp cả khi đồng thời)
        SepayWebhookIngestionService.IngestionResult ingestion =
                webhookIngestionService.ingestEvent(payload);
        if (!ingestion.newlyCreated()
                && (ingestion.currentStatus() == SepayTransactionStatus.PROCESSED
                        || ingestion.currentStatus() == SepayTransactionStatus.IGNORED)) {
            log.info("SePay transaction id {} has already been processed", payload.getId());
            return SepayWebhookResponse.builder()
                    .success(true)
                    .message("Transaction already processed")
                    .build();
        }

        // 5. Pha 2 (REQUIRES_NEW): Khớp chính xác lệnh VietQR và ghi sổ, hoặc định tuyến đối soát Finance
        try {
            return webhookIngestionService.postTransaction(ingestion.transactionId());
        } catch (SepayWebhookIngestionService.PostingBusinessRejectionException rej) {
            return webhookIngestionService.markReconciliationFromRejection(
                    ingestion.transactionId(), rej);
        } catch (RuntimeException ex) {
            log.error(
                    "Transient error posting SePay webhook sepayId={} txId={}: {}",
                    payload.getId(),
                    ingestion.transactionId(),
                    ex.getMessage(),
                    ex);
            webhookIngestionService.markRetryRequired(ingestion.transactionId(), ex.getMessage());
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SEPAY_WEBHOOK_POSTING_RETRYABLE",
                    "Sự kiện SePay đã được lưu nhưng tạm thời chưa thể ghi sổ. Có thể thử xử lý"
                            + " lại an toàn.");
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
        } else if (!canReadAll) {
            throw new AccessDeniedException(
                    "Bạn không có quyền tra cứu trạng thái giao dịch này");
        }

        Booking booking = payment.getBooking();
        BigDecimal totalPaid =
                booking != null ? calculateVerifiedNetPaidReadOnly(booking) : BigDecimal.ZERO;
        BigDecimal remaining =
                booking != null
                        ? BookingPaymentBalanceHelper.outstandingBalance(booking, totalPaid)
                        : BigDecimal.ZERO;
        boolean fullyPaid = booking != null && remaining.signum() == 0;
        boolean isConfirmed =
                booking != null && booking.getBookingStatus() == BookingStatus.Confirmed;
        BookingContractStatus contractStatus =
                booking != null ? resolveContractStatus(booking.getBookingId()) : null;

        return PaymentStatusCheckResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking != null ? booking.getBookingId() : null)
                .bookingReference(
                        booking != null
                                ? booking.getBookingReference()
                                : payment.getInternalReceiptReference())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .requestedAmount(payment.getRequestedAmount())
                .confirmedAmount(payment.getConfirmedAmount())
                .currency(payment.getCurrency())
                .requestedAt(payment.getRequestedAt())
                .confirmedAt(payment.getConfirmedAt())
                .processorReference(payment.getProcessorReference())
                .bookingStatus(booking != null ? booking.getBookingStatus() : null)
                .isBookingConfirmed(isConfirmed)
                .contractStatus(contractStatus)
                .totalPaidAmount(totalPaid)
                .remainingBookingBalance(remaining)
                .fullyPaid(fullyPaid)
                .transferContent(payment.getInternalReceiptReference())
                .paymentType(payment.getCheckoutPaymentType())
                .installmentId(
                        payment.getInstallment() != null
                                ? payment.getInstallment().getInstallmentId()
                                : null)
                .sequenceNo(
                        payment.getInstallment() != null
                                ? payment.getInstallment().getSequenceNo()
                                : null)
                .failureReason(payment.getFailureReason())
                .holdExpiresAt(booking != null ? booking.getHoldExpiresAt() : null)
                .provider(payment.getProvider())
                .build();
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Phân bổ số tiền đã thanh toán ròng (Confirmed Collect trừ Completed Refund) theo cơ chế tuần
     * tự Waterfall cho các đợt thanh toán (Installments).
     */
    public Map<UUID, BigDecimal> calculateWaterfallInstallmentPaidAmounts(
            UUID bookingId, List<Installment> installments) {
        if (installments == null || installments.isEmpty()) {
            return new HashMap<>();
        }
        Booking booking =
                installments.get(0).getBooking() != null
                        ? installments.get(0).getBooking()
                        : bookingRepository.findById(bookingId).orElse(null);
        BigDecimal netPaid =
                booking != null
                        ? calculateVerifiedNetPaidReadOnly(booking)
                        : calculateVerifiedNetPaidByIdReadOnly(bookingId);
        return BookingPaymentBalanceHelper.allocateWaterfall(netPaid, installments);
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

    private BigDecimal calculateVerifiedNetPaid(Booking booking) {
        if (booking == null || booking.getBookingId() == null) {
            return BigDecimal.ZERO;
        }
        List<Payment> payments = paymentRepository.findForBooking(booking.getBookingId());
        List<Refund> refunds =
                refundRepository != null
                        ? refundRepository.findForBooking(booking.getBookingId())
                        : List.of();
        return BookingPaymentBalanceHelper.verifiedNetPaid(booking, payments, refunds);
    }

    private BigDecimal calculateVerifiedNetPaidReadOnly(Booking booking) {
        if (booking == null || booking.getBookingId() == null) {
            return BigDecimal.ZERO;
        }
        List<Payment> payments = paymentRepository.findForBookingReadOnly(booking.getBookingId());
        List<Refund> refunds =
                refundRepository != null
                        ? refundRepository.findForBooking(booking.getBookingId())
                        : List.of();
        return BookingPaymentBalanceHelper.verifiedNetPaid(booking, payments, refunds);
    }

    private BigDecimal calculateVerifiedNetPaidByIdReadOnly(UUID bookingId) {
        if (bookingId == null) {
            return BigDecimal.ZERO;
        }
        Booking stub = new Booking();
        stub.setBookingId(bookingId);
        List<Payment> payments = paymentRepository.findForBookingReadOnly(bookingId);
        List<Refund> refunds =
                refundRepository != null ? refundRepository.findForBooking(bookingId) : List.of();
        return BookingPaymentBalanceHelper.verifiedNetPaid(stub, payments, refunds);
    }

    private BookingContractStatus resolveContractStatus(UUID bookingId) {
        if (bookingContractRepository == null || bookingId == null) {
            return null;
        }
        return bookingContractRepository
                .findByBookingBookingId(bookingId)
                .map(BookingContract::getStatus)
                .orElse(null);
    }

    private BigDecimal toUsdAmount(
            BigDecimal bookingAmount, boolean isBookingVnd, BigDecimal exchangeRate) {
        BigDecimal value = bookingAmount != null ? bookingAmount : BigDecimal.ZERO;
        return isBookingVnd ? value.divide(exchangeRate, 4, RoundingMode.HALF_UP) : value;
    }

    private BigDecimal toVndAmount(
            BigDecimal bookingAmount, boolean isBookingVnd, BigDecimal exchangeRate) {
        BigDecimal value = bookingAmount != null ? bookingAmount : BigDecimal.ZERO;
        return isBookingVnd
                ? value.setScale(0, RoundingMode.HALF_UP)
                : value.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
    }

    private PaymentPlan resolvePaymentPlan(List<Installment> installments) {
        if (installments == null || installments.isEmpty()) {
            return null;
        }
        if (installments.size() == 1) return PaymentPlan.FULL;
        if (installments.size() == 2) return PaymentPlan.DEPOSIT;
        return PaymentPlan.STAGES;
    }

    private String resolveStageDescription(int totalStages, Installment inst) {
        if (totalStages == 1) {
            return "Đợt duy nhất: 100% tiền vé";
        }
        if (totalStages == 2) {
            return inst.getSequenceNo() == 1
                    ? "Đợt 1: Đặt cọc 30%"
                    : "Đợt 2: Thanh toán 70% còn lại";
        }
        return switch (inst.getSequenceNo()) {
            case 1 -> "Đợt 1: Đặt cọc 30%";
            case 2 -> "Đợt 2: Thanh toán giữa kỳ 40%";
            default -> "Đợt " + inst.getSequenceNo() + ": Thanh toán 30% còn lại";
        };
    }

    private Installment findFirstUnpaidInstallment(
            List<Installment> installments, Map<UUID, BigDecimal> paidMap) {
        if (installments == null) {
            return null;
        }
        for (Installment inst : installments) {
            if (inst.getStatus() == InstallmentStatus.Cancelled) {
                continue;
            }
            BigDecimal paid = paidMap.getOrDefault(inst.getInstallmentId(), BigDecimal.ZERO);
            if (paid.compareTo(inst.getRequiredAmount()) < 0) {
                return inst;
            }
        }
        return null;
    }

    private BookingPaymentOptionsResponse.RecommendedPaymentOption buildRecommendedOption(
            Booking booking,
            PaymentPlan plan,
            boolean canCollect,
            BigDecimal outstanding,
            BigDecimal fullVnd,
            Instant fullDueAt,
            BookingPaymentOptionsResponse.DepositPaymentOption depositOption,
            BookingPaymentOptionsResponse.StageDetail currentDueStage) {
        if (!canCollect) {
            return null;
        }
        boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());
        if (plan == PaymentPlan.FULL || (depositOption == null && currentDueStage == null)) {
            return BookingPaymentOptionsResponse.RecommendedPaymentOption.builder()
                    .paymentType(PaymentType.FULL)
                    .label("Thanh toán toàn bộ")
                    .description("Thanh toán 100% số tiền còn lại của đặt chỗ")
                    .amount(outstanding)
                    .amountVnd(fullVnd)
                    .currency(booking.getCurrency())
                    .dueAt(fullDueAt)
                    .build();
        }
        if (plan == PaymentPlan.DEPOSIT && depositOption != null && depositOption.isAvailable()) {
            BigDecimal amt =
                    isBookingVnd
                            ? depositOption.getRemainingAmountVnd()
                            : depositOption.getRemainingAmountUsd();
            return BookingPaymentOptionsResponse.RecommendedPaymentOption.builder()
                    .paymentType(PaymentType.DEPOSIT)
                    .installmentId(depositOption.getInstallmentId())
                    .sequenceNo(depositOption.getSequenceNo())
                    .label("Thanh toán đặt cọc (Đợt 1)")
                    .description(depositOption.getDescription())
                    .amount(amt)
                    .amountVnd(depositOption.getRemainingAmountVnd())
                    .currency(booking.getCurrency())
                    .dueAt(depositOption.getDueAt())
                    .build();
        }
        if (currentDueStage != null) {
            BigDecimal amt =
                    isBookingVnd
                            ? currentDueStage.getRemainingAmountVnd()
                            : currentDueStage.getRemainingAmountUsd();
            return BookingPaymentOptionsResponse.RecommendedPaymentOption.builder()
                    .paymentType(PaymentType.STAGE)
                    .installmentId(currentDueStage.getInstallmentId())
                    .sequenceNo(currentDueStage.getSequenceNo())
                    .label("Thanh toán đợt " + currentDueStage.getSequenceNo())
                    .description(currentDueStage.getDescription())
                    .amount(amt)
                    .amountVnd(currentDueStage.getRemainingAmountVnd())
                    .currency(booking.getCurrency())
                    .dueAt(currentDueStage.getDueAt())
                    .build();
        }
        return BookingPaymentOptionsResponse.RecommendedPaymentOption.builder()
                .paymentType(PaymentType.FULL)
                .label("Thanh toán số dư còn lại")
                .description("Thanh toán toàn bộ số dư còn lại của đặt chỗ")
                .amount(outstanding)
                .amountVnd(fullVnd)
                .currency(booking.getCurrency())
                .dueAt(fullDueAt)
                .build();
    }

    private List<Payment> findActivePendingVietQrPayments(UUID bookingId) {
        List<Payment> direct = paymentRepository.findActivePendingSepayVietQrForBooking(bookingId);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        List<Payment> all = paymentRepository.findByBooking_BookingId(bookingId);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        return all.stream()
                .filter(
                        p ->
                                p.getStatus() == PaymentStatus.Pending
                                        && p.getPaymentMethod() == PaymentMethod.Sepay
                                        && (p.getProvider() == null
                                                || "SEPAY_VIETQR".equalsIgnoreCase(p.getProvider())))
                .sorted(
                        Comparator.comparing(
                                Payment::getRequestedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private SepayCheckoutResponse findActivePendingVietQrResponse(
            Booking booking,
            BookingContractStatus contractStatus,
            List<Installment> installments,
            Map<UUID, BigDecimal> installmentPaidMap,
            BigDecimal outstanding,
            boolean canCollect,
            BigDecimal exchangeRate) {
        List<Payment> activeList = findActivePendingVietQrPayments(booking.getBookingId());
        if (activeList.isEmpty()) {
            return null;
        }
        Payment pending = activeList.get(0);
        PaymentType effectiveType = resolveStoredPaymentType(pending, installments);
        BigDecimal expectedNow =
                computeCurrentExpectedAmountForPending(
                        pending, effectiveType, installments, installmentPaidMap, outstanding);
        boolean isStale =
                !canCollect
                        || pending.getRequestedAmount() == null
                        || expectedNow == null
                        || pending.getRequestedAmount().compareTo(expectedNow) != 0;
        String desc =
                effectiveType == PaymentType.FULL
                        ? "Thanh toán toàn bộ 100% số dư còn lại"
                        : (pending.getInstallment() != null
                                ? resolveStageDescription(
                                        installments.size(), pending.getInstallment())
                                : "Thanh toán đặt chỗ");
        return buildVietQrResponseFromPayment(
                pending, booking, contractStatus, effectiveType, desc, exchangeRate, isStale);
    }

    private PaymentType resolveStoredPaymentType(Payment payment, List<Installment> installments) {
        if (payment.getCheckoutPaymentType() != null) {
            return payment.getCheckoutPaymentType();
        }
        if (payment.getInstallment() == null) {
            return PaymentType.FULL;
        }
        if (payment.getInstallment().getSequenceNo() != null
                && payment.getInstallment().getSequenceNo() == 1
                && installments != null
                && installments.size() == 2) {
            return PaymentType.DEPOSIT;
        }
        return PaymentType.STAGE;
    }

    private BigDecimal computeCurrentExpectedAmountForPending(
            Payment pending,
            PaymentType type,
            List<Installment> installments,
            Map<UUID, BigDecimal> paidMap,
            BigDecimal outstanding) {
        if (type == PaymentType.FULL) {
            return outstanding;
        }
        if (pending.getInstallment() != null) {
            BigDecimal paid =
                    paidMap.getOrDefault(
                            pending.getInstallment().getInstallmentId(), BigDecimal.ZERO);
            return pending.getInstallment()
                    .getRequiredAmount()
                    .subtract(paid)
                    .max(BigDecimal.ZERO)
                    .min(outstanding);
        }
        return outstanding;
    }

    private void assertIdempotencyScopeMatches(
            Payment existing,
            UUID bookingId,
            PaymentType requestedType,
            UUID targetInstallmentId,
            String paymentCurrency) {
        boolean bookingMatches =
                existing.getBooking() != null
                        && Objects.equals(existing.getBooking().getBookingId(), bookingId);
        boolean typeMatches =
                existing.getCheckoutPaymentType() == null
                        || existing.getCheckoutPaymentType() == requestedType;
        UUID existingInstId =
                existing.getInstallment() != null
                        ? existing.getInstallment().getInstallmentId()
                        : null;
        boolean installmentMatches =
                requestedType == PaymentType.FULL
                        || Objects.equals(existingInstId, targetInstallmentId);
        String existingCurrency =
                existing.getOriginalCurrency() != null
                        ? existing.getOriginalCurrency()
                        : existing.getCurrency();
        boolean currencyMatches =
                existingCurrency == null || existingCurrency.equalsIgnoreCase(paymentCurrency);
        boolean methodMatches = existing.getPaymentMethod() == PaymentMethod.Sepay;

        if (!bookingMatches
                || !typeMatches
                || !installmentMatches
                || !currencyMatches
                || !methodMatches) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_CONFLICT",
                    "Mã yêu cầu thanh toán đã tồn tại với phạm vi hoặc khoản thanh toán khác.");
        }
    }

    private boolean isSameCheckoutOption(
            Payment existing,
            PaymentType requestedType,
            UUID targetInstallmentId,
            String paymentCurrency) {
        boolean typeMatches =
                existing.getCheckoutPaymentType() == null
                        || existing.getCheckoutPaymentType() == requestedType;
        UUID existingInstId =
                existing.getInstallment() != null
                        ? existing.getInstallment().getInstallmentId()
                        : null;
        boolean instMatches =
                requestedType == PaymentType.FULL
                        || Objects.equals(existingInstId, targetInstallmentId);
        String existingCurrency =
                existing.getOriginalCurrency() != null
                        ? existing.getOriginalCurrency()
                        : existing.getCurrency();
        boolean currencyMatches =
                existingCurrency == null || existingCurrency.equalsIgnoreCase(paymentCurrency);
        return typeMatches && instMatches && currencyMatches;
    }

    private String resolveScopeSuffix(PaymentType paymentType, Installment targetInstallment) {
        if (paymentType == PaymentType.FULL) {
            return "F";
        }
        if (targetInstallment != null && targetInstallment.getSequenceNo() != null) {
            return "S" + targetInstallment.getSequenceNo();
        }
        return paymentType == PaymentType.DEPOSIT ? "S1" : "F";
    }

    private String generateUniqueTransferContent(
            UUID bookingId, String cleanRef, PaymentType paymentType, Installment targetInstallment) {
        String baseSuffix = resolveScopeSuffix(paymentType, targetInstallment);
        String baseContent = "CASMS " + cleanRef + baseSuffix;

        List<Payment> existingForBooking = paymentRepository.findByBooking_BookingId(bookingId);
        if (existingForBooking == null || existingForBooking.isEmpty()) {
            return baseContent;
        }
        Set<String> usedCodes = new HashSet<>();
        for (Payment p : existingForBooking) {
            if (p.getInternalReceiptReference() != null && !p.getInternalReceiptReference().isBlank()) {
                usedCodes.add(
                        p.getInternalReceiptReference()
                                .replaceAll("[^A-Za-z0-9]", "")
                                .toUpperCase(Locale.ROOT));
            }
        }
        if (!usedCodes.contains(baseContent.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT))) {
            return baseContent;
        }
        for (int attempt = 2; attempt <= 99; attempt++) {
            String candidate = baseContent + "A" + attempt;
            if (!usedCodes.contains(
                    candidate.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return baseContent + "A" + (System.currentTimeMillis() % 10000);
    }

    private String buildVietQrUrl(
            String accountNumber, String bankName, BigDecimal amountVnd, String transferContent) {
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        return String.format(
                "https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s&template=%s",
                accountNumber,
                bankName,
                amountVnd.toPlainString(),
                encodedContent,
                sepayProperties.getQrTemplate());
    }

    private SepayCheckoutResponse buildVietQrResponseFromPayment(
            Payment payment,
            Booking booking,
            BookingContractStatus contractStatus,
            PaymentType fallbackType,
            String stageDesc,
            BigDecimal fallbackExchangeRate,
            boolean stale) {
        boolean isBookingVnd = "VND".equalsIgnoreCase(booking.getCurrency());
        BigDecimal rate =
                payment.getExchangeRateApplied() != null
                                && payment.getExchangeRateApplied().signum() > 0
                        ? payment.getExchangeRateApplied()
                        : fallbackExchangeRate;
        BigDecimal baseAmount =
                payment.getRequestedAmount() != null ? payment.getRequestedAmount() : BigDecimal.ZERO;
        BigDecimal amountUsd = toUsdAmount(baseAmount, isBookingVnd, rate);
        BigDecimal amountVnd = toVndAmount(baseAmount, isBookingVnd, rate);
        String payCurrency =
                payment.getOriginalCurrency() != null && !payment.getOriginalCurrency().isBlank()
                        ? payment.getOriginalCurrency().trim()
                        : (isBookingVnd ? "VND" : "USD");
        BigDecimal chargedAmount =
                payment.getOriginalAmount() != null
                        ? payment.getOriginalAmount()
                        : ("USD".equalsIgnoreCase(payCurrency) ? amountUsd : amountVnd);

        String bankName =
                payment.getQrBankName() != null
                        ? payment.getQrBankName()
                        : sepayProperties.getBankName();
        String accountNumber =
                payment.getQrAccountNumber() != null
                        ? payment.getQrAccountNumber()
                        : sepayProperties.getAccountNumber();
        String accountHolder =
                payment.getQrAccountHolderName() != null
                        ? payment.getQrAccountHolderName()
                        : sepayProperties.getAccountName();
        String transferContent =
                payment.getInternalReceiptReference() != null
                        ? payment.getInternalReceiptReference()
                        : "CASMS "
                                + booking.getBookingReference().replaceAll("[^A-Za-z0-9]", "")
                                + resolveScopeSuffix(fallbackType, payment.getInstallment());
        String qrUrl =
                payment.getQrCodeUrl() != null
                        ? payment.getQrCodeUrl()
                        : buildVietQrUrl(accountNumber, bankName, amountVnd, transferContent);

        PaymentType effectiveType =
                payment.getCheckoutPaymentType() != null
                        ? payment.getCheckoutPaymentType()
                        : fallbackType;

        String instructions =
                "USD".equalsIgnoreCase(payCurrency)
                        ? "Thanh toán qua SePay theo đơn vị USD ("
                                + amountUsd
                                + " USD ~ "
                                + amountVnd
                                + " VNĐ) hoặc mở ứng dụng ngân hàng quét mã QR với nội dung '"
                                + transferContent
                                + "'."
                        : "Mở ứng dụng ngân hàng quét mã QR hoặc chuyển khoản chính xác "
                                + amountVnd.toPlainString()
                                + " VNĐ với đúng nội dung '"
                                + transferContent
                                + "'. Hệ thống sẽ tự động ghi nhận ngay khi ngân hàng báo tiền về.";

        return SepayCheckoutResponse.builder()
                .paymentId(payment.getPaymentId())
                .bookingId(booking.getBookingId())
                .bookingReference(booking.getBookingReference())
                .paymentType(effectiveType)
                .installmentId(
                        payment.getInstallment() != null
                                ? payment.getInstallment().getInstallmentId()
                                : null)
                .sequenceNo(
                        payment.getInstallment() != null
                                ? payment.getInstallment().getSequenceNo()
                                : null)
                .stageDescription(stageDesc)
                .amountUsd(amountUsd)
                .amountVnd(amountVnd)
                .exchangeRate(rate)
                .paymentCurrency(payCurrency)
                .chargedAmount(chargedAmount)
                .paymentChannel("VIETQR")
                .supportedCurrencies(resolveSupportedCurrencies())
                .supportedCardBrands(resolveSupportedCardBrands())
                .bankName(bankName)
                .accountNumber(accountNumber)
                .accountHolderName(accountHolder)
                .transferContent(transferContent)
                .qrCodeUrl(qrUrl)
                .status(payment.getStatus())
                .createdAt(payment.getRequestedAt())
                .idempotencyKey(payment.getIdempotencyKey())
                .installmentDueAt(
                        payment.getInstallment() != null
                                ? payment.getInstallment().getDueAt()
                                : booking.getFinalPaymentDueAt())
                .bookingHoldExpiresAt(booking.getHoldExpiresAt())
                .expiresAt(payment.getQrExpiresAt())
                .stale(stale)
                .bookingStatus(booking.getBookingStatus())
                .contractStatus(contractStatus)
                .instructions(instructions)
                .build();
    }

    private void assertAccess(Booking booking, UUID userId, boolean canReadAll) {
        if (canReadAll) {
            return;
        }
        if (userId == null
                || booking == null
                || booking.getBookedByUser() == null
                || !userId.equals(booking.getBookedByUser().getUserId())) {
            throw new AccessDeniedException(
                    "Bạn chỉ có thể xem hoặc thực hiện thanh toán cho đơn của chính mình");
        }
    }
}
