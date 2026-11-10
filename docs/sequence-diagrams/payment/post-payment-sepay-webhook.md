# POST /payment/sepay/webhook

```mermaid
sequenceDiagram
    title POST /payment/sepay/webhook — SepayController.handleWebhook
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as SepayController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /payment/sepay/webhook + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke handleWebhook(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: sepayService.processWebhook()

    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: for Payment p : pendingPayments
    Note over Controller,Service: Source branch: for Payment p : sortedByReceipt
    Note over Controller,Service: Source branch: if !cleanDigits.isBlank()
    Note over Controller,Service: Source branch: if !cleanKey.isBlank() && cleanMemo.contains(cleanKey)
    Note over Controller,Service: Source branch: if !cleanReceipt.isBlank() && cleanMemo.contains(cleanReceipt)
    Note over Controller,Service: Source branch: if !pendingPayments.isEmpty()
    Note over Controller,Service: Source branch: if !sepayProperties.isEnabled()
    Note over Controller,Service: Source branch: if cleanDigits.length() == 4
    Note over Controller,Service: Source branch: if cleanDigits.length() >= 8
    Note over Controller,Service: Source branch: if directOpt.isPresent()
    Note over Controller,Service: Source branch: if matchedBooking != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: InstallmentRepository.findByBookingBookingIdOrderBySequenceNoAsc()
            Service->>Repository: PaymentRepository.save()
            Service->>Repository: SepayTransactionRepository.existsBySepayId()
            Service->>Repository: SepayTransactionRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```

## CASMS-92 integration review

See [reviewed end-to-end flow](../CASMS-92-BOOKING-FLOWS.md): payment confirmation can atomically enqueue booking confirmation mail; passenger itinerary keeps bookingPassenger/cruise scope. Other branches retain prior review status.
