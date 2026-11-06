# POST /payment/sepay/checkout

```mermaid
sequenceDiagram
    title POST /payment/sepay/checkout — SepayController.createCheckout
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as SepayController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /payment/sepay/checkout + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createCheckout(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: sepayService.createCheckout()

    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: if !pendingList.isEmpty()
    Note over Controller,Service: Source branch: if booking.getBookingStatus(
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Cancelled
    Note over Controller,Service: Source branch: if existingPaymentOpt.isPresent()
    Note over Controller,Service: Source branch: if installments.isEmpty()
    Note over Controller,Service: Source branch: if outstanding.compareTo(BigDecimal.ZERO) <= 0
    Note over Controller,Service: Source branch: if paid.compareTo(inst.getRequiredAmount()) < 0
    Note over Controller,Service: Source branch: if payment.getStatus() == PaymentStatus.Confirmed
    Note over Controller,Service: Source branch: if remainingForDeposit.compareTo(BigDecimal.ZERO) <= 0
    Note over Controller,Service: Source branch: if remainingForStage.compareTo(BigDecimal.ZERO) <= 0
    Note over Controller,Service: Source branch: if request.getInstallmentId() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: InstallmentRepository.findByBookingBookingIdOrderBySequenceNoAsc()
            Service->>Repository: PaymentRepository.findByBooking_BookingId()
            Service->>Repository: PaymentRepository.findByIdempotencyKey()
            Service->>Repository: PaymentRepository.findForBooking()
            Service->>Repository: PaymentRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
