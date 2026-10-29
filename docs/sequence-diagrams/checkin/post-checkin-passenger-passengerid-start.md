# POST /checkin/passenger/{passengerId}/start

```mermaid
sequenceDiagram
    title POST /checkin/passenger/{passengerId}/start — CheckinController.startCheckin
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CheckinController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /checkin/passenger/{passengerId}/start + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke startCheckin(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.startCheckin()

    Note over Controller,Service: Source branch: if !staff && (userId == null || bookingPassenger.getPassenger().getUser(
    Note over Controller,Service: Source branch: if checkin.getStartedAt() == null
    Note over Controller,Service: Source branch: if emergencyName != null && !emergencyName.isBlank()
    Note over Controller,Service: Source branch: if emergencyPhone != null && !emergencyPhone.isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.findById()
            Service->>Repository: CruiseCheckinRepository.findByBookingPassenger_BookingPassengerId()
            Service->>Repository: CruiseCheckinRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
