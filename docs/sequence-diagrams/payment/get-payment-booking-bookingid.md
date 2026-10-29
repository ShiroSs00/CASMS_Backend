# GET /payment/booking/{bookingId}

```mermaid
sequenceDiagram
    title GET /payment/booking/{bookingId} — PaymentController.getPaymentsForBooking
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PaymentController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /payment/booking/{bookingId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getPaymentsForBooking(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.getPaymentsForBooking()

    Note over Controller,Service: Source branch: if !canReadAll && (userId == null || booking.getBookedByUser(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingRepository.findById()
            Service->>Repository: PaymentRepository.findByBooking_BookingId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws org.springframework.security.access.AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
