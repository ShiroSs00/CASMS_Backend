# GET /payment/sepay/options/{bookingId}

```mermaid
sequenceDiagram
    title GET /payment/sepay/options/{bookingId} — SepayController.getPaymentOptions
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as SepayController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /payment/sepay/options/{bookingId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getPaymentOptions(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: sepayService.getPaymentOptions()

    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: if !installments.isEmpty()
    Note over Controller,Service: Source branch: if !isPaid && !foundCurrentDue && outstanding.compareTo(BigDecimal.ZERO) > 0
    Note over Controller,Service: Source branch: if alloc.compareTo(BigDecimal.ZERO) < 0
    Note over Controller,Service: Source branch: if installments == null || installments.isEmpty()
    Note over Controller,Service: Source branch: if installments.size() == 1
    Note over Controller,Service: Source branch: if installments.size() == 2
    Note over Controller,Service: Source branch: if installments.size() >= 3
    Note over Controller,Service: Source branch: if outstanding.compareTo(BigDecimal.ZERO) < 0
    Note over Controller,Service: Source branch: if remInst.compareTo(BigDecimal.ZERO
    Note over Controller,Service: Source branch: if remInst1.compareTo(BigDecimal.ZERO
    Note over Controller,Service: Source branch: if remainingToAllocate.compareTo(BigDecimal.ZERO) < 0
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: InstallmentRepository.findByBookingBookingIdOrderBySequenceNoAsc()
            Service->>Repository: PaymentRepository.findForBooking()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
