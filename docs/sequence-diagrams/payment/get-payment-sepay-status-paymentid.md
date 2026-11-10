# GET /payment/sepay/status/{paymentId}

```mermaid
sequenceDiagram
    title GET /payment/sepay/status/{paymentId} — SepayController.getPaymentStatus
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as SepayController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /payment/sepay/status/{paymentId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getPaymentStatus(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: sepayService.getPaymentStatus()

    Note over Controller,Service: Source branch: if payment.getBooking() != null
    Note over Controller,Service: Source branch: if remaining.compareTo(BigDecimal.ZERO) < 0
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    end
```

## CASMS-92 status correction

`isBookingConfirmed` is true only for a Confirmed booking. A settled payment while the physical contract is unsigned leaves the booking unconfirmed. See [reviewed booking flows](../CASMS-92-BOOKING-FLOWS.md). Other branches retain prior review status.
