# POST /payment/process

```mermaid
sequenceDiagram
    title POST /payment/process — PaymentController.createPayment
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PaymentController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /payment/process + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createPayment(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.createPayment()

    Note over Controller,Service: Source branch: for Installment inst : insts
    Note over Controller,Service: Source branch: if !bookingMatches || !amountMatches || !currencyMatches || !installmentMatches
    Note over Controller,Service: Source branch: if !targetInstallment.getBooking().getBookingId().equals(booking.getBookingId())
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Cancelled
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Draft && booking.getHoldExpiresAt(
    Note over Controller,Service: Source branch: if booking.getCurrency(
    Note over Controller,Service: Source branch: if confirmedForInst.compareTo(inst.getRequiredAmount()) < 0
    Note over Controller,Service: Source branch: if existingPayment.isPresent()
    Note over Controller,Service: Source branch: if outstandingBalance.compareTo(BigDecimal.ZERO) < 0
    Note over Controller,Service: Source branch: if remainingForInst.compareTo(BigDecimal.ZERO) < 0
    Note over Controller,Service: Source branch: if request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
    Note over Controller,Service: Source branch: if request.getInstallmentId() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingRepository.findById()
            Service->>Repository: InstallmentRepository.findByBookingBookingIdOrderBySequenceNoAsc()
            Service->>Repository: InstallmentRepository.findById()
            Service->>Repository: PaymentRepository.findByIdempotencyKey()
            Service->>Repository: PaymentRepository.findForBooking()
            Service->>Repository: PaymentRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
