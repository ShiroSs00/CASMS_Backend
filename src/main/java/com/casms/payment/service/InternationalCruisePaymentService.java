package com.casms.payment.service;

import com.casms.booking.entity.Booking;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.payment.dto.v1.InternationalPaymentDtos.*;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.gateway.PaymentGateway;
import com.casms.payment.gateway.PaymentGateway.GatewayPaymentRequest;
import com.casms.payment.gateway.PaymentGateway.GatewayResult;
import com.casms.payment.repository.*;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternationalCruisePaymentService {

    private final OnboardAccountRepository onboardAccountRepository;
    private final OnboardAccountMemberRepository onboardAccountMemberRepository;
    private final SpendingLimitRepository spendingLimitRepository;
    private final OnboardTransactionRepository onboardTransactionRepository;
    private final RegisteredPaymentMethodRepository registeredPaymentMethodRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final OfflineSyncLogRepository offlineSyncLogRepository;
    private final FinalSettlementRepository finalSettlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final ChargeDisputeRepository chargeDisputeRepository;
    private final InvoiceRepository invoiceRepository;
    private final CruiseRepository cruiseRepository;
    private final PassengerRepository passengerRepository;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final PaymentGateway paymentGateway;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // 1. ONBOARD ACCOUNT MANAGEMENT
    // =========================================================================
    @Transactional
    public OnboardAccountResponse createOrActivateAccount(
            CreateOnboardAccountRequest request,
            CasmsPrincipal principal) {
        Cruise cruise = cruiseRepository.findById(request.cruiseId())
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", request.cruiseId()));
        Passenger passenger = passengerRepository.findById(request.passengerId())
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", request.passengerId()));

        Optional<OnboardAccount> existingOpt =
                onboardAccountRepository.findByCruise_CruiseIdAndPrimaryPassenger_PassengerId(
                        cruise.getCruiseId(), passenger.getPassengerId());

        String cruiseBaseCurrency = cruise.getBaseCurrency() != null && !cruise.getBaseCurrency().isBlank()
                ? cruise.getBaseCurrency().trim().toUpperCase(Locale.ROOT)
                : "USD";
        if (request.baseCurrency() != null && !request.baseCurrency().isBlank()
                && !request.baseCurrency().trim().equalsIgnoreCase(cruiseBaseCurrency)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ACCOUNT_BASE_CURRENCY_MISMATCH",
                    "Onboard account base currency must match the cruise base currency (" + cruiseBaseCurrency + ")");
        }

        BigDecimal limit = request.spendingLimit() != null
                ? request.spendingLimit().setScale(4, RoundingMode.HALF_UP)
                : new BigDecimal("2000.0000");

        boolean activate = request.activateImmediately() == null || request.activateImmediately();

        OnboardAccount account = existingOpt.orElseGet(OnboardAccount::new);
        account.setCruise(cruise);
        account.setPrimaryPassenger(passenger);
        account.setBaseCurrency(cruiseBaseCurrency);
        account.setCreditLimit(limit);
        if (activate) {
            account.setStatus(OnboardAccountStatus.ACTIVE);
            if (account.getActivatedAt() == null) {
                account.setActivatedAt(Instant.now());
            }
        } else if (account.getStatus() == null) {
            account.setStatus(OnboardAccountStatus.PENDING_ACTIVATION);
        }

        OnboardAccount savedAccount = onboardAccountRepository.save(account);

        // Ensure primary passenger is registered as OWNER member with spending limit
        List<OnboardAccountMember> existingMembers =
                onboardAccountMemberRepository.findByOnboardAccount_OnboardAccountId(savedAccount.getOnboardAccountId());
        OnboardAccountMember ownerMember = existingMembers.stream()
                .filter(m -> m.getPassenger().getPassengerId().equals(passenger.getPassengerId()))
                .findFirst()
                .orElseGet(() -> {
                    OnboardAccountMember m = new OnboardAccountMember();
                    m.setOnboardAccount(savedAccount);
                    m.setPassenger(passenger);
                    m.setMemberRole(MemberRole.OWNER);
                    m.setChargePermission(ChargePermission.ALLOWED);
                    m.setStatus(AccountMemberStatus.ACTIVE);
                    m.setEffectiveFrom(Instant.now());
                    return onboardAccountMemberRepository.save(m);
                });

        SpendingLimit spendingLimit = new SpendingLimit();
        spendingLimit.setAccountMember(ownerMember);
        spendingLimit.setLimitAmount(limit);
        spendingLimit.setCurrencyCode(cruiseBaseCurrency);
        spendingLimit.setEffectiveFrom(Instant.now());
        spendingLimit.setStatus(SpendingLimitStatus.ACTIVE);
        if (principal != null && principal.getUser() != null) {
            spendingLimit.setSetByUser(principal.getUser());
        }
        spendingLimitRepository.save(spendingLimit);

        return toAccountResponse(savedAccount);
    }

    @Transactional(readOnly = true)
    public OnboardAccountResponse getAccount(UUID accountId, CasmsPrincipal principal) {
        OnboardAccount account = onboardAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", accountId));
        assertAccountReadAccess(account, principal);
        return toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalance(
            UUID accountId,
            String displayCurrency,
            CasmsPrincipal principal) {
        OnboardAccount account = onboardAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", accountId));
        assertAccountReadAccess(account, principal);

        LedgerTotals totals = computeLedgerTotals(account);
        String baseCurr = account.getBaseCurrency() != null ? account.getBaseCurrency().trim() : "USD";

        String targetDisplay = displayCurrency != null && !displayCurrency.isBlank()
                ? displayCurrency.trim().toUpperCase(Locale.ROOT)
                : baseCurr;

        var convOut = currencyExchangeService.convertForDisplay(totals.outstandingBalance(), baseCurr, targetDisplay);
        var convAvail = currencyExchangeService.convertForDisplay(totals.availableLimit(), baseCurr, targetDisplay);

        return new AccountBalanceResponse(
                account.getOnboardAccountId(),
                account.getPrimaryPassenger() != null ? account.getPrimaryPassenger().getPassengerId() : null,
                account.getCruise() != null ? account.getCruise().getCruiseId() : null,
                baseCurr,
                totals.spendingLimit(),
                totals.totalCharges(),
                totals.totalRefunds(),
                totals.totalPayments(),
                totals.outstandingBalance(),
                totals.availableLimit(),
                account.getStatus(),
                targetDisplay,
                convOut.convertedAmount(),
                convAvail.convertedAmount(),
                convOut.exchangeRate(),
                "Informational conversion only. Historical onboard charges remain in " + baseCurr + "."
        );
    }

    @Transactional
    public OnboardAccountResponse updateSpendingLimit(
            UUID accountId,
            UpdateSpendingLimitRequest request,
            CasmsPrincipal principal) {
        OnboardAccount account = onboardAccountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", accountId));

        if (Boolean.TRUE.equals(request.financeStaffOverride())) {
            assertFinanceStaffAccess(principal, "Only authorized Finance Staff can approve spending-limit exceptions");
        } else {
            assertStaffManageAccess(principal, "Not authorized to modify onboard spending limits");
        }

        if (account.getStatus() == OnboardAccountStatus.CLOSED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ACCOUNT_CLOSED",
                    "Cannot modify spending limit of a closed onboard account");
        }

        LedgerTotals totals = computeLedgerTotals(account);
        BigDecimal newLimit = request.newSpendingLimit().setScale(4, RoundingMode.HALF_UP);
        if (newLimit.compareTo(totals.outstandingBalance()) < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "LIMIT_BELOW_OUTSTANDING_BALANCE",
                    "New spending limit (" + newLimit + ") cannot be lower than current outstanding balance ("
                            + totals.outstandingBalance() + ")");
        }

        account.setCreditLimit(newLimit);
        OnboardAccount saved = onboardAccountRepository.save(account);
        return toAccountResponse(saved);
    }

    // =========================================================================
    // 2. ONBOARD TRANSACTIONS (WITH PESSIMISTIC CONCURRENCY LOCKING)
    // =========================================================================
    @Transactional
    public TransactionResponse recordTransaction(
            CreateTransactionRequest request,
            CasmsPrincipal principal) {
        String idempotencyKey = request.idempotencyKey().trim();
        Optional<OnboardTransaction> existingTx = onboardTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info("Idempotent hit for onboard transaction key={}", idempotencyKey);
            return toTransactionResponse(existingTx.get());
        }

        // Pessimistic write lock on OnboardAccount to prevent concurrent spending-limit violations
        OnboardAccount account = onboardAccountRepository.findByIdForUpdate(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", request.accountId()));

        if (account.getStatus() != OnboardAccountStatus.ACTIVE) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ACCOUNT_NOT_ACTIVE",
                    "Onboard account must be ACTIVE before making purchases (current status: " + account.getStatus() + ")");
        }

        String baseCurrency = account.getBaseCurrency() != null ? account.getBaseCurrency().trim() : "USD";
        if (!baseCurrency.equalsIgnoreCase(request.currency().trim())) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSACTION_CURRENCY_MISMATCH",
                    "All onboard transactions must be recorded in the cruise base currency (" + baseCurrency + ")");
        }

        TransactionType txType = request.transactionType() != null ? request.transactionType() : TransactionType.CHARGE;
        BigDecimal amount = request.amount().setScale(4, RoundingMode.HALF_UP);

        if (txType == TransactionType.CHARGE || txType == TransactionType.ADJUSTMENT) {
            LedgerTotals totals = computeLedgerTotals(account);
            if (amount.compareTo(totals.availableLimit()) > 0) {
                if (Boolean.TRUE.equals(request.staffLimitOverrideApproved())) {
                    assertFinanceStaffAccess(principal, "Only authorized Finance Staff can approve spending-limit overrides");
                    account.setCreditLimit(account.getCreditLimit().add(amount.subtract(totals.availableLimit())));
                    onboardAccountRepository.save(account);
                } else {
                    throw new BusinessException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "SPENDING_LIMIT_EXCEEDED",
                            "Transaction amount " + amount + " " + baseCurrency
                                    + " exceeds available spending limit " + totals.availableLimit() + " " + baseCurrency);
                }
            }
        }

        Passenger passenger = request.passengerId() != null
                ? passengerRepository.findById(request.passengerId()).orElse(account.getPrimaryPassenger())
                : account.getPrimaryPassenger();

        OnboardTransaction tx = new OnboardTransaction();
        tx.setOnboardAccount(account);
        tx.setPassenger(passenger);
        tx.setTransactionType(txType);
        tx.setAmount(amount);
        tx.setCurrencyCode(baseCurrency);
        tx.setTransactionTime(Instant.now());
        tx.setPostedAt(Instant.now());
        tx.setStatus(TransactionStatus.POSTED);
        tx.setSyncStatus(TransactionSyncStatus.SYNCED);
        tx.setSyncedAt(Instant.now());
        tx.setServiceReference(request.serviceReference().trim());
        tx.setSourceType(request.serviceReference().trim());
        tx.setPosTerminalId(request.posTerminalId());
        tx.setDeviceReference(request.deviceReference());
        tx.setIdempotencyKey(idempotencyKey);
        tx.setDescription(request.description() != null ? request.description() : request.serviceReference());
        if (principal != null && principal.getUser() != null) {
            tx.setCreatedByUser(principal.getUser());
        }

        OnboardTransaction saved = onboardTransactionRepository.save(tx);
        return toTransactionResponse(saved);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId, CasmsPrincipal principal) {
        OnboardTransaction tx = onboardTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardTransaction", transactionId));
        assertAccountReadAccess(tx.getOnboardAccount(), principal);
        return toTransactionResponse(tx);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAccountTransactions(UUID accountId, CasmsPrincipal principal) {
        OnboardAccount account = onboardAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", accountId));
        assertAccountReadAccess(account, principal);
        return onboardTransactionRepository
                .findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(accountId)
                .stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse refundTransaction(
            UUID originalTransactionId,
            TransactionActionRequest request,
            CasmsPrincipal principal) {
        String idempotencyKey = request.idempotencyKey().trim();
        Optional<OnboardTransaction> existing = onboardTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toTransactionResponse(existing.get());
        }

        OnboardTransaction original = onboardTransactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardTransaction", originalTransactionId));

        OnboardAccount account = onboardAccountRepository.findByIdForUpdate(
                        original.getOnboardAccount().getOnboardAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", original.getOnboardAccount().getOnboardAccountId()));

        if (original.getTransactionType() != TransactionType.CHARGE || original.getStatus() != TransactionStatus.POSTED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSACTION_NOT_REFUNDABLE",
                    "Only POSTED CHARGE transactions can be refunded");
        }

        BigDecimal refundAmount = request.amount() != null
                ? request.amount().setScale(4, RoundingMode.HALF_UP)
                : original.getAmount();

        BigDecimal alreadyRefunded = onboardTransactionRepository
                .findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(account.getOnboardAccountId())
                .stream()
                .filter(t -> t.getOriginalTransaction() != null
                        && originalTransactionId.equals(t.getOriginalTransaction().getTransactionId())
                        && (t.getTransactionType() == TransactionType.REFUND || t.getTransactionType() == TransactionType.REVERSAL)
                        && t.getStatus() == TransactionStatus.POSTED)
                .map(OnboardTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (alreadyRefunded.add(refundAmount).compareTo(original.getAmount()) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "REFUND_EXCEEDS_ORIGINAL_CHARGE",
                    "Refund amount " + refundAmount + " exceeds remaining refundable charge balance "
                            + original.getAmount().subtract(alreadyRefunded));
        }

        OnboardTransaction refundTx = new OnboardTransaction();
        refundTx.setOnboardAccount(account);
        refundTx.setPassenger(original.getPassenger());
        refundTx.setTransactionType(TransactionType.REFUND);
        refundTx.setAmount(refundAmount);
        refundTx.setCurrencyCode(original.getCurrencyCode());
        refundTx.setTransactionTime(Instant.now());
        refundTx.setPostedAt(Instant.now());
        refundTx.setStatus(TransactionStatus.POSTED);
        refundTx.setSyncStatus(TransactionSyncStatus.SYNCED);
        refundTx.setSyncedAt(Instant.now());
        refundTx.setOriginalTransaction(original);
        refundTx.setServiceReference(original.getServiceReference());
        refundTx.setSourceType(original.getSourceType());
        refundTx.setIdempotencyKey(idempotencyKey);
        refundTx.setDescription("Refund: " + request.reason());
        if (principal != null && principal.getUser() != null) {
            refundTx.setCreatedByUser(principal.getUser());
        }

        return toTransactionResponse(onboardTransactionRepository.save(refundTx));
    }

    @Transactional
    public TransactionResponse voidTransaction(
            UUID originalTransactionId,
            TransactionActionRequest request,
            CasmsPrincipal principal) {
        String idempotencyKey = request.idempotencyKey().trim();
        Optional<OnboardTransaction> existing = onboardTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toTransactionResponse(existing.get());
        }

        OnboardTransaction original = onboardTransactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardTransaction", originalTransactionId));

        OnboardAccount account = onboardAccountRepository.findByIdForUpdate(
                        original.getOnboardAccount().getOnboardAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", original.getOnboardAccount().getOnboardAccountId()));

        if (account.getStatus() == OnboardAccountStatus.CLOSED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ACCOUNT_CLOSED",
                    "Cannot void transaction on a closed onboard account");
        }
        if (original.getStatus() == TransactionStatus.VOIDED) {
            return toTransactionResponse(original);
        }
        if (original.getStatus() != TransactionStatus.POSTED && original.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSACTION_CANNOT_BE_VOIDED",
                    "Transaction in status " + original.getStatus() + " cannot be voided");
        }

        original.setStatus(TransactionStatus.VOIDED);
        onboardTransactionRepository.save(original);

        OnboardTransaction reversal = new OnboardTransaction();
        reversal.setOnboardAccount(account);
        reversal.setPassenger(original.getPassenger());
        reversal.setTransactionType(TransactionType.REVERSAL);
        reversal.setAmount(original.getAmount());
        reversal.setCurrencyCode(original.getCurrencyCode());
        reversal.setTransactionTime(Instant.now());
        reversal.setPostedAt(Instant.now());
        reversal.setStatus(TransactionStatus.POSTED);
        reversal.setSyncStatus(TransactionSyncStatus.SYNCED);
        reversal.setSyncedAt(Instant.now());
        reversal.setOriginalTransaction(original);
        reversal.setServiceReference(original.getServiceReference());
        reversal.setIdempotencyKey(idempotencyKey);
        reversal.setDescription("VOID Reversal: " + request.reason());
        if (principal != null && principal.getUser() != null) {
            reversal.setCreatedByUser(principal.getUser());
        }

        return toTransactionResponse(onboardTransactionRepository.save(reversal));
    }

    // =========================================================================
    // 3. TOKENIZED PAYMENT METHODS (NEVER STORE RAW CARD NUMBERS OR CVV)
    // =========================================================================
    @Transactional
    public RegisteredPaymentMethodResponse registerPaymentMethod(
            RegisterPaymentMethodRequest request,
            CasmsPrincipal principal) {
        Passenger passenger = passengerRepository.findById(request.passengerId())
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", request.passengerId()));
        assertPassengerAccess(passenger, principal);

        String token = request.paymentToken().trim();
        // Security check: Reject if caller accidentally sends a raw 13-19 digit PAN as token
        String digitsOnly = token.replaceAll("[\\s-]", "");
        if (digitsOnly.matches("\\d{13,19}")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "RAW_CARD_DATA_FORBIDDEN",
                    "Security violation: Never send or store raw card numbers (PAN). Provide a gateway payment token.");
        }

        Optional<RegisteredPaymentMethod> existingToken = registeredPaymentMethodRepository.findByPaymentToken(token);
        if (existingToken.isPresent()) {
            return toPaymentMethodResponse(existingToken.get());
        }

        if (Boolean.TRUE.equals(request.setAsDefault())) {
            List<RegisteredPaymentMethod> activeMethods =
                    registeredPaymentMethodRepository.findByPassenger_PassengerIdAndStatusOrderByCreatedAtDesc(
                            passenger.getPassengerId(), "ACTIVE");
            for (RegisteredPaymentMethod pm : activeMethods) {
                if (pm.isDefault()) {
                    pm.setDefault(false);
                    registeredPaymentMethodRepository.save(pm);
                }
            }
        }

        RegisteredPaymentMethod method = new RegisteredPaymentMethod();
        method.setPassenger(passenger);
        method.setProvider(request.provider().trim().toUpperCase(Locale.ROOT));
        method.setPaymentToken(token);
        method.setProviderCustomerReference(request.providerCustomerReference());
        method.setCardBrand(request.cardBrand().trim().toUpperCase(Locale.ROOT));
        method.setCardLast4(request.cardLast4().trim());
        method.setCardHolderName(request.cardHolderName());
        method.setExpiryMonth(request.expiryMonth());
        method.setExpiryYear(request.expiryYear());
        method.setIssuingCountry(request.issuingCountry() != null ? request.issuingCountry().trim().toUpperCase(Locale.ROOT) : "US");
        method.setIssuingCurrency(request.issuingCurrency() != null ? request.issuingCurrency().trim().toUpperCase(Locale.ROOT) : "USD");
        method.setDefault(Boolean.TRUE.equals(request.setAsDefault()));
        method.setStatus("ACTIVE");

        return toPaymentMethodResponse(registeredPaymentMethodRepository.save(method));
    }

    @Transactional(readOnly = true)
    public List<RegisteredPaymentMethodResponse> getPaymentMethods(
            UUID passengerId,
            CasmsPrincipal principal) {
        if (passengerId != null) {
            Passenger passenger = passengerRepository.findById(passengerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Passenger", passengerId));
            assertPassengerAccess(passenger, principal);
            return registeredPaymentMethodRepository.findByPassenger_PassengerIdOrderByCreatedAtDesc(passengerId)
                    .stream()
                    .map(this::toPaymentMethodResponse)
                    .toList();
        }
        return registeredPaymentMethodRepository.findAll().stream()
                .map(this::toPaymentMethodResponse)
                .toList();
    }

    @Transactional
    public void revokePaymentMethod(UUID paymentMethodId, CasmsPrincipal principal) {
        RegisteredPaymentMethod method = registeredPaymentMethodRepository.findById(paymentMethodId)
                .orElseThrow(() -> new ResourceNotFoundException("RegisteredPaymentMethod", paymentMethodId));
        assertPassengerAccess(method.getPassenger(), principal);
        method.setStatus("REVOKED");
        method.setDefault(false);
        registeredPaymentMethodRepository.save(method);
    }

    // =========================================================================
    // 4. INTERNATIONAL PAYMENT GATEWAY OPERATIONS (AUTHORIZE, CAPTURE, CHARGE, REFUND, WEBHOOK)
    // =========================================================================
    @Transactional
    public GatewayPaymentResponse authorizePayment(
            AuthorizePaymentRequest request,
            CasmsPrincipal principal) {
        String idempotencyKey = request.idempotencyKey().trim();
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toGatewayPaymentResponse(existing.get());
        }

        PaymentTarget target = resolvePaymentTarget(request.accountId(), request.bookingId(), principal);
        TokenResolution tokenRes = resolveToken(request.registeredPaymentMethodId(), request.paymentToken(), target.account());

        String baseCurr = request.currency().trim().toUpperCase(Locale.ROOT);
        BigDecimal requestedAmount = request.amount().setScale(4, RoundingMode.HALF_UP);

        String settlementCurr = request.settlementCurrency() != null && !request.settlementCurrency().isBlank()
                ? request.settlementCurrency().trim().toUpperCase(Locale.ROOT)
                : baseCurr;

        var fxResult = currencyExchangeService.convertForSettlement(
                requestedAmount, baseCurr, settlementCurr, Boolean.TRUE.equals(request.customerDccConsent()));

        Payment payment = new Payment();
        payment.setOnboardAccount(target.account());
        payment.setBooking(target.booking());
        payment.setRegisteredPaymentMethod(tokenRes.method());
        payment.setProvider(tokenRes.provider());
        payment.setPaymentToken(tokenRes.token());
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.TokenizedCard);
        payment.setRequestedAmount(requestedAmount);
        payment.setCurrency(baseCurr);
        payment.setOriginalAmount(requestedAmount);
        payment.setOriginalCurrency(baseCurr);
        payment.setConvertedAmount(fxResult.convertedAmount());
        payment.setSettlementCurrency(settlementCurr);
        payment.setExchangeRateApplied(fxResult.exchangeRate());
        payment.setExchangeRateSource(fxResult.rateSource());
        payment.setExchangeRateTimestamp(fxResult.effectiveAt());
        payment.setRequestedAt(Instant.now());
        payment.setStatus(PaymentStatus.Initiated);
        payment.setIdempotencyKey(idempotencyKey);
        payment = paymentRepository.save(payment);

        // Call external payment gateway abstraction
        GatewayResult gwResult = paymentGateway.authorize(new GatewayPaymentRequest(
                tokenRes.token(),
                fxResult.convertedAmount(),
                settlementCurr,
                idempotencyKey,
                request.description() != null ? request.description() : "Cruise Onboard Pre-Authorization",
                target.account() != null ? target.account().getOnboardAccountId().toString() : null
        ));

        recordPaymentAttempt(payment, "AUTHORIZE", fxResult.convertedAmount(), settlementCurr, idempotencyKey, gwResult);

        if (gwResult.successful()) {
            payment.setStatus(PaymentStatus.Authorized);
            payment.setAuthorizedAmount(requestedAmount);
            payment.setAuthorizedAt(gwResult.processedAt());
            payment.setProcessorReference(gwResult.providerTransactionId());

            if (target.account() != null) {
                BigDecimal currentPreAuth = target.account().getPreAuthorizedAmount() != null
                        ? target.account().getPreAuthorizedAmount()
                        : BigDecimal.ZERO;
                target.account().setPreAuthorizedAmount(currentPreAuth.add(requestedAmount));
                onboardAccountRepository.save(target.account());
            }
        } else if (gwResult.uncertainOrTimeout()) {
            payment.setStatus(PaymentStatus.Pending);
            payment.setProcessorReference(gwResult.providerTransactionId());
            payment.setFailureReason(gwResult.errorMessage());
        } else {
            payment.setStatus(PaymentStatus.Failed);
            payment.setProcessorReference(gwResult.providerTransactionId());
            payment.setFailureReason(gwResult.errorMessage());
        }

        Payment saved = paymentRepository.save(payment);
        return toGatewayPaymentResponse(saved);
    }

    @Transactional
    public GatewayPaymentResponse capturePayment(
            CapturePaymentRequest request,
            CasmsPrincipal principal) {
        Payment payment = paymentRepository.findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", request.paymentId()));

        if (payment.getStatus() == PaymentStatus.Captured || payment.getStatus() == PaymentStatus.Confirmed) {
            return toGatewayPaymentResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.Authorized) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_NOT_AUTHORIZED",
                    "Only AUTHORIZED payments can be captured (current status: " + payment.getStatus() + ")");
        }

        BigDecimal captureAmt = request.captureAmount().setScale(4, RoundingMode.HALF_UP);
        BigDecimal maxAuthorized = payment.getAuthorizedAmount() != null
                ? payment.getAuthorizedAmount()
                : payment.getRequestedAmount();
        if (captureAmt.compareTo(maxAuthorized) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT",
                    "Capture amount " + captureAmt + " cannot exceed authorized amount " + maxAuthorized);
        }

        GatewayResult gwResult = paymentGateway.capture(
                payment.getProcessorReference(),
                captureAmt,
                payment.getCurrency(),
                request.idempotencyKey().trim());

        recordPaymentAttempt(payment, "CAPTURE", captureAmt, payment.getCurrency(), request.idempotencyKey().trim(), gwResult);

        if (gwResult.successful()) {
            payment.setStatus(PaymentStatus.Captured);
            payment.setCapturedAmount(captureAmt);
            payment.setConfirmedAmount(captureAmt);
            payment.setCapturedAt(gwResult.processedAt());
            payment.setConfirmedAt(gwResult.processedAt());
        } else {
            payment.setStatus(PaymentStatus.Failed);
            payment.setFailureReason(gwResult.errorMessage());
        }

        Payment saved = paymentRepository.save(payment);
        if (gwResult.successful() && saved.getBooking() != null) {
            paymentService.allocateInstallmentsAndSync(saved.getBooking());
        }
        return toGatewayPaymentResponse(saved);
    }

    @Transactional
    public GatewayPaymentResponse chargePayment(
            DirectChargePaymentRequest request,
            CasmsPrincipal principal) {
        String idempotencyKey = request.idempotencyKey().trim();
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toGatewayPaymentResponse(existing.get());
        }

        PaymentTarget target = resolvePaymentTarget(request.accountId(), request.bookingId(), principal);
        TokenResolution tokenRes = resolveToken(request.registeredPaymentMethodId(), request.paymentToken(), target.account());

        String baseCurr = request.currency().trim().toUpperCase(Locale.ROOT);
        BigDecimal requestedAmount = request.amount().setScale(4, RoundingMode.HALF_UP);
        String settlementCurr = request.settlementCurrency() != null && !request.settlementCurrency().isBlank()
                ? request.settlementCurrency().trim().toUpperCase(Locale.ROOT)
                : baseCurr;

        var fxResult = currencyExchangeService.convertForSettlement(
                requestedAmount, baseCurr, settlementCurr, Boolean.TRUE.equals(request.customerDccConsent()));

        Payment payment = new Payment();
        payment.setOnboardAccount(target.account());
        payment.setBooking(target.booking());
        payment.setRegisteredPaymentMethod(tokenRes.method());
        payment.setProvider(tokenRes.provider());
        payment.setPaymentToken(tokenRes.token());
        payment.setOperationType(PaymentOperationType.Collect);
        payment.setPaymentMethod(PaymentMethod.TokenizedCard);
        payment.setRequestedAmount(requestedAmount);
        payment.setCurrency(baseCurr);
        payment.setOriginalAmount(requestedAmount);
        payment.setOriginalCurrency(baseCurr);
        payment.setConvertedAmount(fxResult.convertedAmount());
        payment.setSettlementCurrency(settlementCurr);
        payment.setExchangeRateApplied(fxResult.exchangeRate());
        payment.setExchangeRateSource(fxResult.rateSource());
        payment.setExchangeRateTimestamp(fxResult.effectiveAt());
        payment.setRequestedAt(Instant.now());
        payment.setStatus(PaymentStatus.Initiated);
        payment.setIdempotencyKey(idempotencyKey);
        payment = paymentRepository.save(payment);

        GatewayResult gwResult = paymentGateway.charge(new GatewayPaymentRequest(
                tokenRes.token(),
                fxResult.convertedAmount(),
                settlementCurr,
                idempotencyKey,
                request.description() != null ? request.description() : "Cruise Direct Charge",
                target.account() != null ? target.account().getOnboardAccountId().toString() : null
        ));

        recordPaymentAttempt(payment, "CHARGE", fxResult.convertedAmount(), settlementCurr, idempotencyKey, gwResult);

        if (gwResult.successful()) {
            payment.setStatus(PaymentStatus.Captured);
            payment.setCapturedAmount(requestedAmount);
            payment.setConfirmedAmount(requestedAmount);
            payment.setCapturedAt(gwResult.processedAt());
            payment.setConfirmedAt(gwResult.processedAt());
            payment.setProcessorReference(gwResult.providerTransactionId());
        } else if (gwResult.uncertainOrTimeout()) {
            payment.setStatus(PaymentStatus.Pending);
            payment.setProcessorReference(gwResult.providerTransactionId());
            payment.setFailureReason(gwResult.errorMessage());
        } else {
            payment.setStatus(PaymentStatus.Failed);
            payment.setProcessorReference(gwResult.providerTransactionId());
            payment.setFailureReason(gwResult.errorMessage());
        }

        Payment saved = paymentRepository.save(payment);
        if (gwResult.successful() && saved.getBooking() != null) {
            paymentService.allocateInstallmentsAndSync(saved.getBooking());
        }
        return toGatewayPaymentResponse(saved);
    }

    @Transactional
    public GatewayPaymentResponse refundGatewayPayment(
            UUID paymentId,
            GatewayRefundRequest request,
            CasmsPrincipal principal) {
        if (principal == null || !principal.hasRole(Role.FINANCE_OFFICER)) {
            throw new AccessDeniedException("Only Finance can refund gateway payments");
        }
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        String requestKey = request.idempotencyKey().strip();
        BigDecimal refundAmt = request.refundAmount().setScale(4, RoundingMode.HALF_UP);
        var prior = paymentAttemptRepository.findFirstByPayment_PaymentIdAndOperationAndIdempotencyKeyOrderByAttemptNoDesc(
                paymentId, "REFUND", requestKey);
        if (prior.isPresent()) {
            var attempt = prior.get();
            if (refundAmt.compareTo(attempt.getAmount()) != 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "GATEWAY_REFUND_REPLAY_CONFLICT",
                        "Khóa hoàn tiền đã được dùng với số tiền khác.");
            }
            if ("SUCCESS".equals(attempt.getStatus())) return toGatewayPaymentResponse(payment);
            throw new BusinessException(HttpStatus.CONFLICT, "GATEWAY_REFUND_REVIEW_REQUIRED",
                    "Cần đối soát lần hoàn tiền trước khi thử lại.");
        }

        if (payment.getStatus() != PaymentStatus.Captured
                && payment.getStatus() != PaymentStatus.Confirmed
                && payment.getStatus() != PaymentStatus.PartiallyRefunded) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_NOT_REFUNDABLE",
                    "Only CAPTURED/CONFIRMED payments can be refunded (current status: " + payment.getStatus() + ")");
        }

        BigDecimal captured = RefundBalance.eligible(payment);
        BigDecimal alreadyRefunded = RefundBalance.gatewayRefunded(payment);
        BigDecimal remaining = RefundBalance.remaining(payment,
                refundRepository.findByOriginalPayment_PaymentIdOrderByRequestedAtDesc(paymentId));

        if (refundAmt.compareTo(remaining) > 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "REFUND_EXCEEDS_REMAINING_CAPTURED_BALANCE",
                    "Refund amount " + refundAmt + " exceeds remaining refundable amount " + remaining);
        }

        GatewayResult gwResult = paymentGateway.refund(
                payment.getProcessorReference(),
                refundAmt,
                payment.getCurrency(),
                request.reason(),
                requestKey);

        recordPaymentAttempt(payment, "REFUND", refundAmt, payment.getCurrency(), requestKey, gwResult);

        if (!gwResult.successful()) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GATEWAY_REFUND_FAILED",
                    gwResult.errorMessage() != null ? gwResult.errorMessage() : "Gateway refund failed");
        }

        BigDecimal newRefundedTotal = alreadyRefunded.add(refundAmt);
        payment.setRefundedAmount(newRefundedTotal);
        if (newRefundedTotal.compareTo(captured) >= 0) {
            payment.setStatus(PaymentStatus.Refunded);
        } else {
            payment.setStatus(PaymentStatus.PartiallyRefunded);
        }

        Payment saved = paymentRepository.save(payment);
        if (gwResult.successful() && saved.getBooking() != null) {
            paymentService.allocateInstallmentsAndSync(saved.getBooking());
        }
        return toGatewayPaymentResponse(saved);
    }

    @Transactional(readOnly = true)
    public GatewayPaymentResponse getGatewayPayment(UUID paymentId, CasmsPrincipal principal) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getOnboardAccount() != null) {
            assertAccountReadAccess(payment.getOnboardAccount(), principal);
        }
        return toGatewayPaymentResponse(payment);
    }

    @Transactional
    public GatewayPaymentResponse queryAndSyncPaymentStatus(UUID paymentId, CasmsPrincipal principal) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getOnboardAccount() != null) {
            assertAccountReadAccess(payment.getOnboardAccount(), principal);
        }

        if (payment.getProcessorReference() != null && payment.getStatus() == PaymentStatus.Pending) {
            GatewayResult gwStatus = paymentGateway.getPaymentStatus(payment.getProcessorReference());
            if (gwStatus.successful()) {
                if ("AUTHORIZED".equals(gwStatus.gatewayStatus())) {
                    payment.setStatus(PaymentStatus.Authorized);
                    payment.setAuthorizedAmount(payment.getRequestedAmount());
                    payment.setAuthorizedAt(Instant.now());
                } else if ("CAPTURED".equals(gwStatus.gatewayStatus())) {
                    payment.setStatus(PaymentStatus.Captured);
                    payment.setCapturedAmount(payment.getRequestedAmount());
                    payment.setConfirmedAmount(payment.getRequestedAmount());
                    payment.setCapturedAt(Instant.now());
                    payment.setConfirmedAt(Instant.now());
                }
                payment = paymentRepository.save(payment);
            }
        }
        return toGatewayPaymentResponse(payment);
    }

    @Transactional
    public Map<String, Object> processGatewayWebhook(
            String provider,
            String signatureHeader,
            String rawPayloadJson,
            WebhookNotificationPayload payload) {
        if (!paymentGateway.verifyWebhookSignature(rawPayloadJson, signatureHeader)) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "WEBHOOK_SIGNATURE_INVALID",
                    "Invalid HMAC-SHA256 webhook signature");
        }

        Optional<PaymentWebhookEvent> existingEvent =
                paymentWebhookEventRepository.findByExternalEventId(payload.externalEventId().trim());
        if (existingEvent.isPresent()) {
            return Map.of(
                    "webhookEventId", existingEvent.get().getWebhookEventId(),
                    "status", "DUPLICATE_IGNORED",
                    "externalEventId", payload.externalEventId());
        }

        Payment payment = null;
        if (payload.paymentId() != null) {
            payment = paymentRepository.findByIdForUpdate(payload.paymentId()).orElse(null);
        }
        if (payment == null && payload.processorReference() != null) {
            payment = paymentRepository.findByProcessorReference(payload.processorReference()).orElse(null);
        }

        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(provider != null ? provider.toUpperCase(Locale.ROOT) : paymentGateway.getProviderName());
        event.setExternalEventId(payload.externalEventId().trim());
        event.setEventType(payload.eventType().trim());
        event.setPayment(payment);
        event.setSignatureVerified(true);
        event.setPayloadJson(rawPayloadJson);
        event.setReceivedAt(Instant.now());

        if (payment != null) {
            switch (payload.eventType().trim().toUpperCase(Locale.ROOT)) {
                case "PAYMENT_AUTHORIZED" -> {
                    payment.setStatus(PaymentStatus.Authorized);
                    payment.setAuthorizedAmount(payload.amount() != null ? payload.amount() : payment.getRequestedAmount());
                    payment.setAuthorizedAt(Instant.now());
                }
                case "PAYMENT_CAPTURED", "PAYMENT_SUCCEEDED" -> {
                    payment.setStatus(PaymentStatus.Captured);
                    BigDecimal amt = payload.amount() != null ? payload.amount() : payment.getRequestedAmount();
                    payment.setCapturedAmount(amt);
                    payment.setConfirmedAmount(amt);
                    payment.setCapturedAt(Instant.now());
                    payment.setConfirmedAt(Instant.now());
                }
                case "PAYMENT_FAILED" -> {
                    payment.setStatus(PaymentStatus.Failed);
                    payment.setFailureReason(payload.failureReason() != null ? payload.failureReason() : "Failed via webhook");
                }
                case "PAYMENT_REFUNDED" -> {
                    payment.setStatus(PaymentStatus.Refunded);
                    payment.setRefundedAmount(payload.amount() != null ? payload.amount() : payment.getRequestedAmount());
                }
                default -> log.info("Unhandled webhook eventType={}", payload.eventType());
            }
            paymentRepository.save(payment);
            event.setProcessingStatus("PROCESSED");
        } else {
            event.setProcessingStatus("IGNORED");
            event.setErrorMessage("Target payment not found");
        }
        event.setProcessedAt(Instant.now());
        PaymentWebhookEvent savedEvent = paymentWebhookEventRepository.save(event);

        return Map.of(
                "webhookEventId", savedEvent.getWebhookEventId(),
                "status", savedEvent.getProcessingStatus(),
                "externalEventId", savedEvent.getExternalEventId());
    }

    // =========================================================================
    // 5. OFFLINE TRANSACTION SYNCHRONIZATION (IDEMPOTENT & CONFLICT DETECTION)
    // =========================================================================
    @Transactional
    public OfflineBatchSyncResponse syncOfflineTransactions(
            OfflineBatchSyncRequest request,
            CasmsPrincipal principal) {
        UUID batchId = request.batchId() != null ? request.batchId() : UUID.randomUUID();
        List<OfflineSyncItemResult> results = new ArrayList<>();

        int syncedCount = 0;
        int duplicateCount = 0;
        int conflictCount = 0;
        int failedCount = 0;

        for (OfflineTransactionItemRequest item : request.transactions()) {
            String idemKey = item.idempotencyKey().trim();
            Instant now = Instant.now();

            // 1. Prevent duplicate transaction processing
            Optional<OnboardTransaction> existingTx = onboardTransactionRepository.findByIdempotencyKey(idemKey);
            if (existingTx.isPresent()) {
                OnboardTransaction dup = existingTx.get();
                saveOfflineSyncLog(batchId, request.posTerminalId(), item.deviceReference(),
                        dup, dup.getOnboardAccount(), idemKey, "DUPLICATE_IGNORED", null,
                        "Idempotent replay: transaction already synchronized", item.occurrenceTime(), now);
                duplicateCount++;
                results.add(new OfflineSyncItemResult(
                        dup.getTransactionId(), idemKey, "DUPLICATE_IGNORED",
                        dup.getSyncStatus(), null, null, item.occurrenceTime(), dup.getSyncedAt()));
                continue;
            }

            Optional<OnboardAccount> accountOpt = onboardAccountRepository.findByIdForUpdate(item.accountId());
            if (accountOpt.isEmpty()) {
                saveOfflineSyncLog(batchId, request.posTerminalId(), item.deviceReference(),
                        null, null, idemKey, "SYNC_FAILED", "ACCOUNT_NOT_FOUND",
                        "Onboard account not found: " + item.accountId(), item.occurrenceTime(), now);
                failedCount++;
                results.add(new OfflineSyncItemResult(
                        null, idemKey, "SYNC_FAILED",
                        TransactionSyncStatus.SYNC_FAILED, "ACCOUNT_NOT_FOUND",
                        "Onboard account not found", item.occurrenceTime(), now));
                continue;
            }

            OnboardAccount account = accountOpt.get();
            String baseCurr = account.getBaseCurrency() != null ? account.getBaseCurrency().trim() : "USD";
            BigDecimal amount = item.amount().setScale(4, RoundingMode.HALF_UP);
            LedgerTotals totals = computeLedgerTotals(account);

            String conflictCode = null;
            String conflictDetail = null;

            if (account.getStatus() != OnboardAccountStatus.ACTIVE) {
                conflictCode = "ACCOUNT_NOT_ACTIVE";
                conflictDetail = "Account was in state " + account.getStatus() + " during offline sync";
            } else if (!baseCurr.equalsIgnoreCase(item.currency().trim())) {
                conflictCode = "CURRENCY_MISMATCH";
                conflictDetail = "Offline transaction currency " + item.currency() + " mismatches base currency " + baseCurr;
            } else if (amount.compareTo(totals.availableLimit()) > 0) {
                conflictCode = "OFFLINE_SPENDING_LIMIT_EXCEEDED";
                conflictDetail = "Offline charge " + amount + " exceeds available limit " + totals.availableLimit();
            }

            Passenger passenger = item.passengerId() != null
                    ? passengerRepository.findById(item.passengerId()).orElse(account.getPrimaryPassenger())
                    : account.getPrimaryPassenger();

            OnboardTransaction tx = new OnboardTransaction();
            tx.setOnboardAccount(account);
            tx.setPassenger(passenger);
            tx.setTransactionType(item.transactionType() != null ? item.transactionType() : TransactionType.CHARGE);
            tx.setAmount(amount);
            tx.setCurrencyCode(baseCurr);
            tx.setTransactionTime(item.occurrenceTime()); // Preserve actual offline occurrence timestamp
            tx.setServiceReference(item.serviceReference());
            tx.setSourceType(item.serviceReference());
            tx.setPosTerminalId(item.posTerminalId() != null ? item.posTerminalId() : request.posTerminalId());
            tx.setDeviceReference(item.deviceReference());
            tx.setIdempotencyKey(idemKey);
            tx.setDescription(item.description() != null ? item.description() : item.serviceReference());
            tx.setSyncedAt(now);
            if (principal != null && principal.getUser() != null) {
                tx.setCreatedByUser(principal.getUser());
            }

            if (conflictCode != null) {
                tx.setStatus(TransactionStatus.FAILED);
                tx.setSyncStatus(TransactionSyncStatus.CONFLICT);
                OnboardTransaction savedConflict = onboardTransactionRepository.save(tx);
                saveOfflineSyncLog(batchId, request.posTerminalId(), item.deviceReference(),
                        savedConflict, account, idemKey, "CONFLICT", conflictCode,
                        conflictDetail, item.occurrenceTime(), now);
                conflictCount++;
                results.add(new OfflineSyncItemResult(
                        savedConflict.getTransactionId(), idemKey, "CONFLICT",
                        TransactionSyncStatus.CONFLICT, conflictCode, conflictDetail, item.occurrenceTime(), now));
            } else {
                tx.setPostedAt(now);
                tx.setStatus(TransactionStatus.POSTED);
                tx.setSyncStatus(TransactionSyncStatus.SYNCED);
                OnboardTransaction savedSynced = onboardTransactionRepository.save(tx);
                saveOfflineSyncLog(batchId, request.posTerminalId(), item.deviceReference(),
                        savedSynced, account, idemKey, "SYNCED", null,
                        null, item.occurrenceTime(), now);
                syncedCount++;
                results.add(new OfflineSyncItemResult(
                        savedSynced.getTransactionId(), idemKey, "SYNCED",
                        TransactionSyncStatus.SYNCED, null, null, item.occurrenceTime(), now));
            }
        }

        return new OfflineBatchSyncResponse(
                batchId,
                request.deviceReference(),
                request.transactions().size(),
                syncedCount,
                duplicateCount,
                conflictCount,
                failedCount,
                Instant.now(),
                results
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOfflineSyncStatus(UUID accountId, UUID batchId) {
        List<OfflineSyncLog> logs;
        if (batchId != null) {
            logs = offlineSyncLogRepository.findByBatchIdOrderBySyncedAtAsc(batchId);
        } else if (accountId != null) {
            logs = offlineSyncLogRepository.findByOnboardAccount_OnboardAccountIdOrderBySyncedAtDesc(accountId);
        } else {
            logs = offlineSyncLogRepository.findTop100ByOrderBySyncedAtDesc();
        }

        long synced = logs.stream().filter(l -> "SYNCED".equals(l.getSyncOutcome())).count();
        long conflicts = logs.stream().filter(l -> "CONFLICT".equals(l.getSyncOutcome())).count();
        long failed = logs.stream().filter(l -> "SYNC_FAILED".equals(l.getSyncOutcome())).count();
        long duplicates = logs.stream().filter(l -> "DUPLICATE_IGNORED".equals(l.getSyncOutcome())).count();

        return Map.of(
                "totalLogs", logs.size(),
                "syncedCount", synced,
                "conflictCount", conflicts,
                "failedCount", failed,
                "duplicateIgnoredCount", duplicates,
                "logs", logs.stream().map(l -> Map.of(
                        "syncLogId", l.getSyncLogId(),
                        "batchId", l.getBatchId(),
                        "idempotencyKey", l.getIdempotencyKey(),
                        "syncOutcome", l.getSyncOutcome(),
                        "conflictCode", l.getConflictCode() != null ? l.getConflictCode() : "",
                        "errorDetail", l.getErrorDetail() != null ? l.getErrorDetail() : "",
                        "occurrenceTime", l.getOccurrenceTime(),
                        "syncedAt", l.getSyncedAt()
                )).toList()
        );
    }

    // =========================================================================
    // 6. END-OF-CRUISE SETTLEMENT & ACCOUNT CLOSURE RECONCILIATION
    // =========================================================================
    @Transactional
    public SettlementStatementResponse initiateSettlement(
            InitiateSettlementRequest request,
            CasmsPrincipal principal) {
        OnboardAccount account = onboardAccountRepository.findByIdForUpdate(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", request.accountId()));
        assertAccountReadAccess(account, principal);

        // 1. Verify all onboard transactions have been synchronized
        List<OnboardTransaction> allTxs = onboardTransactionRepository
                .findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(account.getOnboardAccountId());

        boolean hasUnsyncedOrConflict = allTxs.stream().anyMatch(tx ->
                tx.getSyncStatus() == TransactionSyncStatus.NOT_SYNCED
                        || tx.getSyncStatus() == TransactionSyncStatus.SYNCING
                        || tx.getSyncStatus() == TransactionSyncStatus.PENDING
                        || tx.getSyncStatus() == TransactionSyncStatus.SYNC_FAILED
                        || tx.getSyncStatus() == TransactionSyncStatus.CONFLICT);

        if (hasUnsyncedOrConflict) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "UNSYNCHRONIZED_OFFLINE_TRANSACTIONS",
                    "Cannot initiate end-of-cruise settlement while unsynchronized or conflicted offline transactions exist");
        }

        // 2. Check unresolved charge disputes
        boolean hasOpenDisputes = chargeDisputeRepository
                .findByCharge_Transaction_OnboardAccount_OnboardAccountIdOrderByReportedAtDesc(account.getOnboardAccountId())
                .stream()
                .anyMatch(d -> d.getStatus() == ChargeDisputeStatus.OPEN || d.getStatus() == ChargeDisputeStatus.UNDER_REVIEW);

        LedgerTotals totals = computeLedgerTotals(account);
        String baseCurr = account.getBaseCurrency() != null ? account.getBaseCurrency().trim() : "USD";

        FinalSettlement settlement = new FinalSettlement();
        settlement.setOnboardAccount(account);
        settlement.setBaseCurrency(baseCurr);
        settlement.setSourceCutoffAt(Instant.now());
        settlement.setTotalChargesSnapshot(totals.totalCharges());
        settlement.setTotalRefundsSnapshot(totals.totalRefunds());
        settlement.setTotalPaymentsSnapshot(totals.totalPayments());
        settlement.setPaidAmount(totals.totalPayments());
        settlement.setOutstandingBalanceSnapshot(totals.outstandingBalance());
        settlement.setAmountDueSnapshot(totals.outstandingBalance());
        settlement.setRefundDueSnapshot(totals.refundDue());
        settlement.setStartedAt(Instant.now());
        if (principal != null && principal.getUserId() != null) {
            settlement.setHandledByUserId(principal.getUserId());
        }

        if (hasOpenDisputes) {
            settlement.setStatus(FinalSettlementStatus.Disputed);
            settlement.setNotes("Account has open charge disputes requiring Finance resolution before final closure");
        } else if (totals.outstandingBalance().signum() == 0) {
            settlement.setStatus(FinalSettlementStatus.Completed);
            settlement.setCompletedAt(Instant.now());
            account.setStatus(OnboardAccountStatus.CLOSED);
            account.setClosedAt(Instant.now());
            onboardAccountRepository.save(account);
        } else {
            settlement.setStatus(FinalSettlementStatus.Pending);
        }

        FinalSettlement savedSettlement = finalSettlementRepository.save(settlement);

        // Save itemized SettlementItem rows
        List<SettlementItem> items = new ArrayList<>();
        for (OnboardTransaction tx : allTxs) {
            if (tx.getStatus() == TransactionStatus.POSTED) {
                SettlementItem item = new SettlementItem();
                item.setFinalSettlement(savedSettlement);
                item.setSourceTransaction(tx);
                item.setItemType(tx.getTransactionType().name());
                item.setDescription(tx.getDescription() != null ? tx.getDescription() : tx.getServiceReference());
                item.setAmount(tx.getAmount());
                item.setCurrency(baseCurr);
                item.setOccurredAt(tx.getTransactionTime());
                items.add(settlementItemRepository.save(item));
            }
        }

        return toSettlementResponse(savedSettlement, account, items);
    }

    @Transactional
    public SettlementStatementResponse processSettlement(
            UUID settlementId,
            ProcessSettlementRequest request,
            CasmsPrincipal principal) {
        FinalSettlement settlement = finalSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("FinalSettlement", settlementId));

        OnboardAccount account = onboardAccountRepository.findByIdForUpdate(
                        settlement.getOnboardAccount().getOnboardAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", settlement.getOnboardAccount().getOnboardAccountId()));
        assertAccountReadAccess(account, principal);

        if (settlement.getStatus() == FinalSettlementStatus.Completed
                || settlement.getStatus() == FinalSettlementStatus.Settled) {
            return getSettlement(settlementId, principal);
        }

        if (settlement.getStatus() == FinalSettlementStatus.Disputed
                && !Boolean.TRUE.equals(request.authorizedExceptionClose())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_DISPUTED",
                    "Cannot close account while disputed charges remain unresolved");
        }

        LedgerTotals totals = computeLedgerTotals(account);
        BigDecimal outstanding = totals.outstandingBalance();

        if (outstanding.signum() == 0) {
            settlement.setStatus(FinalSettlementStatus.Completed);
            settlement.setOutstandingBalanceSnapshot(BigDecimal.ZERO);
            settlement.setAmountDueSnapshot(BigDecimal.ZERO);
            settlement.setCompletedAt(Instant.now());
            account.setStatus(OnboardAccountStatus.CLOSED);
            account.setClosedAt(Instant.now());
            onboardAccountRepository.save(account);
            finalSettlementRepository.save(settlement);
            return getSettlement(settlementId, principal);
        }

        BigDecimal amountToCollect = request.paymentAmount() != null
                ? request.paymentAmount().setScale(4, RoundingMode.HALF_UP)
                : outstanding;

        if (amountToCollect.compareTo(outstanding) < 0 && !Boolean.TRUE.equals(request.allowPartialPayment())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PARTIAL_PAYMENT_NOT_PERMITTED",
                    "Partial settlement payment (" + amountToCollect + " < " + outstanding + ") requires allowPartialPayment=true");
        }
        if (amountToCollect.compareTo(outstanding) > 0) {
            amountToCollect = outstanding;
        }

        settlement.setStatus(FinalSettlementStatus.Processing);
        finalSettlementRepository.save(settlement);

        GatewayPaymentResponse paymentResult;
        if (request.existingAuthorizedPaymentId() != null) {
            paymentResult = capturePayment(
                    new CapturePaymentRequest(request.existingAuthorizedPaymentId(), amountToCollect, request.idempotencyKey()),
                    principal);
        } else {
            paymentResult = chargePayment(
                    new DirectChargePaymentRequest(
                            account.getOnboardAccountId(),
                            null,
                            request.registeredPaymentMethodId(),
                            null,
                            amountToCollect,
                            account.getBaseCurrency(),
                            request.settlementCurrency(),
                            request.customerDccConsent(),
                            request.idempotencyKey(),
                            "End-of-Cruise Final Settlement " + settlementId),
                    principal);
        }

        if (paymentResult.status() == PaymentStatus.Captured || paymentResult.status() == PaymentStatus.Confirmed) {
            BigDecimal remainingAfterPayment = outstanding.subtract(amountToCollect).max(BigDecimal.ZERO);
            settlement.setTotalPaymentsSnapshot(totals.totalPayments().add(amountToCollect));
            settlement.setPaidAmount(totals.totalPayments().add(amountToCollect));
            settlement.setOutstandingBalanceSnapshot(remainingAfterPayment);
            settlement.setAmountDueSnapshot(remainingAfterPayment);

            Payment settledPayment = paymentRepository.findById(paymentResult.paymentId()).orElse(null);
            if (settledPayment != null) {
                settledPayment.setFinalSettlement(settlement);
                paymentRepository.save(settledPayment);

                SettlementItem paymentItem = new SettlementItem();
                paymentItem.setFinalSettlement(settlement);
                paymentItem.setSourcePayment(settledPayment);
                paymentItem.setItemType("PAYMENT");
                paymentItem.setDescription("Settlement Payment Ref: " + settledPayment.getProcessorReference());
                paymentItem.setAmount(amountToCollect);
                paymentItem.setCurrency(account.getBaseCurrency());
                paymentItem.setOccurredAt(Instant.now());
                settlementItemRepository.save(paymentItem);
            }

            if (remainingAfterPayment.signum() == 0) {
                settlement.setStatus(FinalSettlementStatus.Completed);
                settlement.setCompletedAt(Instant.now());
                account.setStatus(OnboardAccountStatus.CLOSED);
                account.setClosedAt(Instant.now());
                onboardAccountRepository.save(account);
            } else {
                settlement.setStatus(FinalSettlementStatus.PartiallyPaid);
                settlement.setNotes("Partially settled; remaining balance: " + remainingAfterPayment + " " + account.getBaseCurrency());
            }
        } else {
            settlement.setStatus(FinalSettlementStatus.Failed);
            settlement.setNotes("Payment collection failed: " + paymentResult.failureReason() + ". Safe to retry with a new idempotencyKey.");
        }

        finalSettlementRepository.save(settlement);
        return getSettlement(settlementId, principal);
    }

    @Transactional(readOnly = true)
    public SettlementStatementResponse getSettlement(UUID settlementId, CasmsPrincipal principal) {
        FinalSettlement settlement = finalSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("FinalSettlement", settlementId));
        OnboardAccount account = settlement.getOnboardAccount();
        assertAccountReadAccess(account, principal);

        List<SettlementItem> items = settlementItemRepository
                .findByFinalSettlement_FinalSettlementIdOrderByOccurredAtAsc(settlementId);
        return toSettlementResponse(settlement, account, items);
    }

    @Transactional(readOnly = true)
    public SettlementStatementResponse getAccountSettlement(UUID accountId, CasmsPrincipal principal) {
        OnboardAccount account = onboardAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", accountId));
        assertAccountReadAccess(account, principal);

        FinalSettlement settlement = finalSettlementRepository
                .findFirstByOnboardAccount_OnboardAccountIdOrderByStartedAtDesc(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FinalSettlement for account", accountId));

        List<SettlementItem> items = settlementItemRepository
                .findByFinalSettlement_FinalSettlementIdOrderByOccurredAtAsc(settlement.getFinalSettlementId());
        return toSettlementResponse(settlement, account, items);
    }

    // =========================================================================
    // INTERNAL HELPERS & LEDGER COMPUTATION
    // =========================================================================
    private record LedgerTotals(
            BigDecimal spendingLimit,
            BigDecimal totalCharges,
            BigDecimal totalRefunds,
            BigDecimal totalPayments,
            BigDecimal outstandingBalance,
            BigDecimal refundDue,
            BigDecimal availableLimit
    ) {}

    private LedgerTotals computeLedgerTotals(OnboardAccount account) {
        UUID accountId = account.getOnboardAccountId();
        BigDecimal limit = account.getCreditLimit() != null
                ? account.getCreditLimit().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        List<OnboardTransaction> postedTxs = onboardTransactionRepository
                .findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(accountId)
                .stream()
                .filter(t -> t.getStatus() == TransactionStatus.POSTED)
                .toList();

        BigDecimal totalCharges = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalRefunds = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal txPayments = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (OnboardTransaction tx : postedTxs) {
            if (tx.getTransactionType() == TransactionType.CHARGE
                    || (tx.getTransactionType() == TransactionType.ADJUSTMENT && tx.getAmount().signum() > 0)) {
                totalCharges = totalCharges.add(tx.getAmount());
            } else if (tx.getTransactionType() == TransactionType.REFUND
                    || tx.getTransactionType() == TransactionType.REVERSAL
                    || tx.getTransactionType() == TransactionType.VOID) {
                totalRefunds = totalRefunds.add(tx.getAmount().abs());
            } else if (tx.getTransactionType() == TransactionType.PAYMENT) {
                txPayments = txPayments.add(tx.getAmount().abs());
            }
        }

        BigDecimal gatewayPayments = paymentRepository
                .findByOnboardAccount_OnboardAccountIdOrderByRequestedAtDesc(accountId)
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.Captured
                        || p.getStatus() == PaymentStatus.Confirmed
                        || p.getStatus() == PaymentStatus.PartiallyRefunded)
                .map(p -> {
                    BigDecimal cap = p.getCapturedAmount() != null
                            ? p.getCapturedAmount()
                            : (p.getConfirmedAmount() != null ? p.getConfirmedAmount() : BigDecimal.ZERO);
                    BigDecimal ref = p.getRefundedAmount() != null ? p.getRefundedAmount() : BigDecimal.ZERO;
                    return cap.subtract(ref).max(BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), BigDecimal::add);

        BigDecimal totalPayments = txPayments.add(gatewayPayments).setScale(4, RoundingMode.HALF_UP);
        BigDecimal net = totalCharges.subtract(totalRefunds).subtract(totalPayments).setScale(4, RoundingMode.HALF_UP);

        BigDecimal outstandingBalance = net.max(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        BigDecimal refundDue = net.signum() < 0 ? net.abs() : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal availableLimit = limit.subtract(outstandingBalance).max(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));

        return new LedgerTotals(limit, totalCharges, totalRefunds, totalPayments, outstandingBalance, refundDue, availableLimit);
    }

    private record PaymentTarget(OnboardAccount account, Booking booking) {}

    private PaymentTarget resolvePaymentTarget(UUID accountId, UUID bookingId, CasmsPrincipal principal) {
        OnboardAccount account = null;
        Booking booking = null;
        if (accountId != null) {
            account = onboardAccountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("OnboardAccount", accountId));
            assertAccountReadAccess(account, principal);
        }
        if (bookingId != null) {
            booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        }
        if (account == null && booking == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_TARGET_REQUIRED",
                    "Either accountId or bookingId must be provided");
        }
        return new PaymentTarget(account, booking);
    }

    private record TokenResolution(RegisteredPaymentMethod method, String token, String provider) {}

    private TokenResolution resolveToken(UUID registeredPaymentMethodId, String rawToken, OnboardAccount account) {
        if (registeredPaymentMethodId != null) {
            RegisteredPaymentMethod pm = registeredPaymentMethodRepository.findById(registeredPaymentMethodId)
                    .orElseThrow(() -> new ResourceNotFoundException("RegisteredPaymentMethod", registeredPaymentMethodId));
            if (!"ACTIVE".equalsIgnoreCase(pm.getStatus())) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "PAYMENT_METHOD_INACTIVE",
                        "Registered payment method is " + pm.getStatus());
            }
            return new TokenResolution(pm, pm.getPaymentToken(), pm.getProvider());
        }
        if (rawToken != null && !rawToken.isBlank()) {
            String trimmed = rawToken.trim();
            if (trimmed.replaceAll("[\\s-]", "").matches("\\d{13,19}")) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "RAW_CARD_DATA_FORBIDDEN",
                        "Never pass raw card numbers; use a tokenized payment reference");
            }
            return new TokenResolution(null, trimmed, paymentGateway.getProviderName());
        }
        if (account != null && account.getPrimaryPassenger() != null) {
            Optional<RegisteredPaymentMethod> defaultPm = registeredPaymentMethodRepository
                    .findFirstByPassenger_PassengerIdAndStatusAndIsDefaultTrue(
                            account.getPrimaryPassenger().getPassengerId(), "ACTIVE");
            if (defaultPm.isPresent()) {
                return new TokenResolution(defaultPm.get(), defaultPm.get().getPaymentToken(), defaultPm.get().getProvider());
            }
        }
        throw new BusinessException(
                HttpStatus.BAD_REQUEST,
                "PAYMENT_METHOD_REQUIRED",
                "A registered payment method or payment token is required");
    }

    private void recordPaymentAttempt(
            Payment payment,
            String operation,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            GatewayResult gwResult) {
        int nextNo = paymentAttemptRepository.countByPayment_PaymentId(payment.getPaymentId()) + 1;
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNo(nextNo);
        attempt.setOperation(operation);
        attempt.setProvider(payment.getProvider() != null ? payment.getProvider() : paymentGateway.getProviderName());
        attempt.setProviderTransactionId(gwResult.providerTransactionId());
        attempt.setAmount(amount);
        attempt.setCurrency(currency);
        attempt.setIdempotencyKey(idempotencyKey);
        attempt.setStatus(gwResult.successful() ? "SUCCESS" : (gwResult.uncertainOrTimeout() ? "TIMEOUT" : "FAILED"));
        attempt.setErrorCode(gwResult.errorCode());
        attempt.setErrorMessage(gwResult.errorMessage());
        attempt.setRequestedAt(Instant.now());
        attempt.setCompletedAt(gwResult.processedAt());
        paymentAttemptRepository.save(attempt);
    }

    private void saveOfflineSyncLog(
            UUID batchId,
            UUID posTerminalId,
            String deviceReference,
            OnboardTransaction tx,
            OnboardAccount account,
            String idempotencyKey,
            String outcome,
            String conflictCode,
            String errorDetail,
            Instant occurrenceTime,
            Instant syncedAt) {
        OfflineSyncLog logEntry = new OfflineSyncLog();
        logEntry.setBatchId(batchId);
        logEntry.setPosTerminalId(posTerminalId);
        logEntry.setDeviceReference(deviceReference);
        logEntry.setTransaction(tx);
        logEntry.setOnboardAccount(account);
        logEntry.setIdempotencyKey(idempotencyKey);
        logEntry.setSyncOutcome(outcome);
        logEntry.setConflictCode(conflictCode);
        logEntry.setErrorDetail(errorDetail);
        logEntry.setOccurrenceTime(occurrenceTime);
        logEntry.setSyncedAt(syncedAt);
        offlineSyncLogRepository.save(logEntry);
    }

    // =========================================================================
    // SECURITY & OWNERSHIP VERIFICATION
    // =========================================================================
    private void assertAccountReadAccess(OnboardAccount account, CasmsPrincipal principal) {
        if (principal == null) return;
        if (isStaffOrFinance(principal)) return;

        UUID callerUserId = principal.getUserId();
        if (account.getPrimaryPassenger() != null
                && account.getPrimaryPassenger().getUser() != null
                && callerUserId.equals(account.getPrimaryPassenger().getUser().getUserId())) {
            return;
        }

        boolean isMember = onboardAccountMemberRepository
                .findByOnboardAccount_OnboardAccountId(account.getOnboardAccountId())
                .stream()
                .anyMatch(m -> m.getPassenger() != null
                        && m.getPassenger().getUser() != null
                        && callerUserId.equals(m.getPassenger().getUser().getUserId()));

        if (!isMember) {
            throw new AccessDeniedException("Passengers may only access their own onboard account and financial records");
        }
    }

    private void assertPassengerAccess(Passenger passenger, CasmsPrincipal principal) {
        if (principal == null) return;
        if (isStaffOrFinance(principal)) return;
        if (passenger.getUser() != null && principal.getUserId().equals(passenger.getUser().getUserId())) {
            return;
        }
        throw new AccessDeniedException("Passengers may only access their own payment methods");
    }

    private void assertFinanceStaffAccess(CasmsPrincipal principal, String message) {
        if (principal == null) {
            throw new AccessDeniedException(message);
        }
        boolean allowed = principal.hasRole(Role.FINANCE_OFFICER)
                || principal.hasRole(Role.ADMIN)
                || hasPermSafe(principal, Permission.PAYMENT_PROCESS)
                || hasPermSafe(principal, Permission.SETTLEMENT_PROCESS);
        if (!allowed) {
            throw new AccessDeniedException(message);
        }
    }

    private void assertStaffManageAccess(CasmsPrincipal principal, String message) {
        if (principal == null) {
            throw new AccessDeniedException(message);
        }
        if (!isStaffOrFinance(principal)) {
            throw new AccessDeniedException(message);
        }
    }

    private boolean isStaffOrFinance(CasmsPrincipal principal) {
        return principal.hasRole(Role.FINANCE_OFFICER)
                || principal.hasRole(Role.OPERATIONS_MANAGER)
                || principal.hasRole(Role.ONBOARD_STAFF)
                || principal.hasRole(Role.BOOKING_AGENT)
                || principal.hasRole(Role.ADMIN)
                || hasPermSafe(principal, Permission.PAYMENT_PROCESS)
                || hasPermSafe(principal, Permission.PAYMENT_REPORT)
                || hasPermSafe(principal, Permission.SETTLEMENT_PROCESS)
                || hasPermSafe(principal, Permission.POS_MANAGE)
                || hasPermSafe(principal, Permission.POS_ACCESS);
    }

    private boolean hasPermSafe(CasmsPrincipal principal, Permission permission) {
        return principal != null
                && principal.getRolePermissionRepository() != null
                && principal.hasPermission(permission);
    }

    // =========================================================================
    // ENTITY -> DTO MAPPERS
    // =========================================================================
    private OnboardAccountResponse toAccountResponse(OnboardAccount account) {
        LedgerTotals totals = computeLedgerTotals(account);
        return new OnboardAccountResponse(
                account.getOnboardAccountId(),
                account.getPrimaryPassenger() != null ? account.getPrimaryPassenger().getPassengerId() : null,
                account.getPrimaryPassenger() != null ? account.getPrimaryPassenger().getFullName() : null,
                account.getCruise() != null ? account.getCruise().getCruiseId() : null,
                account.getCruise() != null ? account.getCruise().getCruiseCode() : null,
                account.getBaseCurrency() != null ? account.getBaseCurrency().trim() : "USD",
                totals.spendingLimit(),
                account.getPreAuthorizedAmount() != null ? account.getPreAuthorizedAmount() : BigDecimal.ZERO,
                totals.totalCharges(),
                totals.totalRefunds(),
                totals.totalPayments(),
                totals.outstandingBalance(),
                totals.availableLimit(),
                account.getStatus(),
                account.getActivatedAt(),
                account.getClosedAt(),
                account.getCreatedAt()
        );
    }

    private TransactionResponse toTransactionResponse(OnboardTransaction tx) {
        return new TransactionResponse(
                tx.getTransactionId(),
                tx.getOnboardAccount() != null ? tx.getOnboardAccount().getOnboardAccountId() : null,
                tx.getPassenger() != null ? tx.getPassenger().getPassengerId() : null,
                tx.getServiceReference() != null ? tx.getServiceReference() : tx.getSourceType(),
                tx.getAmount(),
                tx.getCurrencyCode() != null ? tx.getCurrencyCode().trim() : "USD",
                tx.getTransactionType(),
                tx.getStatus(),
                tx.getSyncStatus(),
                tx.getTransactionTime(),
                tx.getSyncedAt(),
                tx.getPosTerminalId(),
                tx.getDeviceReference(),
                tx.getIdempotencyKey(),
                tx.getOriginalTransaction() != null ? tx.getOriginalTransaction().getTransactionId() : null,
                tx.getCreatedByUser() != null ? tx.getCreatedByUser().getUserId() : null,
                tx.getDescription()
        );
    }

    private RegisteredPaymentMethodResponse toPaymentMethodResponse(RegisteredPaymentMethod pm) {
        return new RegisteredPaymentMethodResponse(
                pm.getPaymentMethodId(),
                pm.getPassenger() != null ? pm.getPassenger().getPassengerId() : null,
                pm.getProvider(),
                pm.getPaymentToken(),
                pm.getCardBrand(),
                pm.getCardLast4() != null ? pm.getCardLast4().trim() : null,
                pm.getCardHolderName(),
                pm.getExpiryMonth(),
                pm.getExpiryYear(),
                pm.getIssuingCountry() != null ? pm.getIssuingCountry().trim() : null,
                pm.getIssuingCurrency() != null ? pm.getIssuingCurrency().trim() : "USD",
                pm.isDefault(),
                pm.getStatus(),
                pm.getCreatedAt()
        );
    }

    private GatewayPaymentResponse toGatewayPaymentResponse(Payment p) {
        List<PaymentAttemptDto> attempts = paymentAttemptRepository
                .findByPayment_PaymentIdOrderByAttemptNoAsc(p.getPaymentId())
                .stream()
                .map(a -> new PaymentAttemptDto(
                        a.getAttemptId(),
                        a.getAttemptNo(),
                        a.getOperation(),
                        a.getProvider(),
                        a.getProviderTransactionId(),
                        a.getAmount(),
                        a.getCurrency() != null ? a.getCurrency().trim() : "USD",
                        a.getStatus(),
                        a.getErrorCode(),
                        a.getErrorMessage(),
                        a.getRequestedAt(),
                        a.getCompletedAt()
                ))
                .toList();

        return new GatewayPaymentResponse(
                p.getPaymentId(),
                p.getOnboardAccount() != null ? p.getOnboardAccount().getOnboardAccountId() : null,
                p.getBooking() != null ? p.getBooking().getBookingId() : null,
                p.getRegisteredPaymentMethod() != null ? p.getRegisteredPaymentMethod().getPaymentMethodId() : null,
                p.getProvider(),
                p.getProcessorReference(),
                p.getStatus(),
                p.getRequestedAmount(),
                p.getAuthorizedAmount(),
                p.getCapturedAmount(),
                p.getRefundedAmount(),
                p.getCurrency() != null ? p.getCurrency().trim() : "USD",
                p.getConvertedAmount(),
                p.getSettlementCurrency() != null ? p.getSettlementCurrency().trim() : null,
                p.getExchangeRateApplied(),
                p.getExchangeRateSource(),
                p.getIdempotencyKey(),
                p.getFailureReason(),
                p.getRequestedAt(),
                p.getAuthorizedAt(),
                p.getCapturedAt(),
                attempts
        );
    }

    private SettlementStatementResponse toSettlementResponse(
            FinalSettlement s,
            OnboardAccount account,
            List<SettlementItem> items) {
        List<SettlementItemDto> itemDtos = items.stream()
                .map(i -> new SettlementItemDto(
                        i.getSettlementItemId(),
                        i.getSourceTransaction() != null ? i.getSourceTransaction().getTransactionId() : null,
                        i.getSourcePayment() != null ? i.getSourcePayment().getPaymentId() : null,
                        i.getItemType(),
                        i.getDescription(),
                        i.getAmount(),
                        i.getCurrency() != null ? i.getCurrency().trim() : "USD",
                        i.getOccurredAt()
                ))
                .toList();

        return new SettlementStatementResponse(
                s.getFinalSettlementId(),
                account.getOnboardAccountId(),
                s.getBaseCurrency() != null ? s.getBaseCurrency().trim() : "USD",
                s.getTotalChargesSnapshot(),
                s.getTotalRefundsSnapshot(),
                s.getTotalPaymentsSnapshot(),
                s.getOutstandingBalanceSnapshot(),
                s.getRefundDueSnapshot(),
                s.getStatus(),
                account.getStatus(),
                s.getStartedAt(),
                s.getCompletedAt(),
                s.getNotes(),
                itemDtos
        );
    }
}

