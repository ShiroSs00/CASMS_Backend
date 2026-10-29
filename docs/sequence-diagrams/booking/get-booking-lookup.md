# GET /booking/lookup

```mermaid
sequenceDiagram
    title GET /booking/lookup — BookingController.lookup
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /booking/lookup + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke lookup(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.lookupBooking()

    Note over Controller,Service: Source branch: for BookingPassenger bp : bookingPassengers
    Note over Controller,Service: Source branch: for BookingPassenger bp : passengers
    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: if !matched
    Note over Controller,Service: Source branch: if !matched && (email != null || phoneNumber != null)
    Note over Controller,Service: Source branch: if (email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank())
    Note over Controller,Service: Source branch: if auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal
    Note over Controller,Service: Source branch: if booking.getBookedByUser() != null && normEmail.equalsIgnoreCase(booking.getBookedByUser().getEmail())
    Note over Controller,Service: Source branch: if bp.getPassenger() != null
    Note over Controller,Service: Source branch: if bpp != null && bpp.getTourPackage() != null
    Note over Controller,Service: Source branch: if c.getDeck() != null
    Note over Controller,Service: Source branch: if email != null && !email.isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.findByBookingBookingId()
            Service->>Repository: BookingRepository.findByBookingReference()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
