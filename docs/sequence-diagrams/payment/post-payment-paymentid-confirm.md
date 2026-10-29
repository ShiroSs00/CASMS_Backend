# POST /payment/{paymentId}/confirm

```mermaid
sequenceDiagram
    title POST /payment/{paymentId}/confirm — PaymentController.confirmPayment
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PaymentController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /payment/{paymentId}/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirmPayment(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.confirmPayment()

    Note over Controller,Service: Source branch: for Payment p : confirmedPayments
    Note over Controller,Service: Source branch: if confirmedAmount == null || confirmedAmount.signum(
    Note over Controller,Service: Source branch: if installments.isEmpty()
    Note over Controller,Service: Source branch: if neededForCurrent.compareTo(BigDecimal.ZERO) <= 0
    Note over Controller,Service: Source branch: if p.getConfirmedAmount() != null
    Note over Controller,Service: Source branch: if p.getInstallment() == null && instIdx < installments.size()
    Note over Controller,Service: Source branch: if payment.getBooking().getBookingStatus(
    Note over Controller,Service: Source branch: if payment.getBooking().getBookingStatus() == BookingStatus.Cancelled
    Note over Controller,Service: Source branch: if payment.getCurrency() == null || payment.getBooking().getCurrency(
    Note over Controller,Service: Source branch: if payment.getOperationType() != PaymentOperationType.Collect || payment.getBooking() == null
    Note over Controller,Service: Source branch: if payment.getPaymentMethod() == PaymentMethod.Cash
    Note over Controller,Service: Source branch: if payment.getStatus() != PaymentStatus.Pending
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: PaymentRepository.existsByInternalReceiptReferenceAndPaymentIdNot()
            Service->>Repository: PaymentRepository.existsByProcessorReferenceAndPaymentIdNot()
            Service->>Repository: PaymentRepository.findByIdForUpdate()
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
