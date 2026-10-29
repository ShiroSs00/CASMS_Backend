# POST /checkin/passenger/{passengerId}/complete

```mermaid
sequenceDiagram
    title POST /checkin/passenger/{passengerId}/complete — CheckinController.completeCheckin
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CheckinController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /checkin/passenger/{passengerId}/complete + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke completeCheckin(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.completeCheckin()

    Note over Controller,Service: Source branch: if !staff && (userId == null || bookingPassenger.getPassenger().getUser(
    Note over Controller,Service: Source branch: if booking.getBookingStatus() != BookingStatus.Confirmed
    Note over Controller,Service: Source branch: if checkin.getCheckinStatus() == CruiseCheckinStatus.BoardingReady
    Note over Controller,Service: Source branch: if checkin.getEmergencyContactName() == null || checkin.getEmergencyContactName().isBlank(
    Note over Controller,Service: Source branch: if contractOpt.isEmpty() || contractOpt.get().getStatus() != BookingContractStatus.Accepted
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingContractRepository.findByBookingBookingId()
            Service->>Repository: CruiseCheckinRepository.findByBookingPassenger_BookingPassengerId()
            Service->>Repository: CruiseCheckinRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
