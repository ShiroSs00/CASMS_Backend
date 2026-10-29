# POST /activities/excursions/{id}/registrations

```mermaid
sequenceDiagram
    title POST /activities/excursions/{id}/registrations — ActivityController.registerExcursion
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /activities/excursions/{id}/registrations + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke registerExcursion(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.registerExcursion()

    Note over Controller,Service: Source branch: if !bp.getBooking().getCruise().getCruiseId().equals(e.getCruiseStop().getItinerary().getCruise().getCruiseId())
    Note over Controller,Service: Source branch: if !staff&&(bp.getPassenger().getUser()==null||!bp.getPassenger().getUser().getUserId().equals(userId))
    Note over Controller,Service: Source branch: if bp.getBooking().getBookingStatus()!=BookingStatus.Confirmed)throw new IllegalStateException("Booking is not confirmed"
    Note over Controller,Service: Source branch: if e.getStatus()!=ShoreExcursionStatus.OPEN||!e.getPlannedReturnTime().isAfter(Instant.now())
    Note over Controller,Service: Source branch: if occupiedExcursion(excursionId)+effectiveQuantity>e.getMaximumCapacity()
    Note over Controller,Service: Source branch: if occupiedExcursion(excursionId)>=e.getMaximumCapacity())e.setStatus(ShoreExcursionStatus.FULL
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.findById()
            Service->>Repository: RegistrationRepository.existsByBookingPassenger_BookingPassengerIdAndShoreExcursion_ShoreExcursionIdAndStatusIn()
            Service->>Repository: RegistrationRepository.save()
            Service->>Repository: ShoreExcursionRepository.lockById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws org.springframework.security.access.AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
