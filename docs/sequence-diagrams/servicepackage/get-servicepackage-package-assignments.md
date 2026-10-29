# GET /servicepackage/package-assignments

```mermaid
sequenceDiagram
    title GET /servicepackage/package-assignments — ServicepackageController.packageAssignments
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /servicepackage/package-assignments + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke packageAssignments(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.packageAssignments()

    Note over Controller,Service: Source branch: if !isOwner
    Note over Controller,Service: Source branch: if !staff
    Note over Controller,Service: Source branch: if bookingId!=null
    Note over Controller,Service: Source branch: if staff)return assignmentRepo.findAll();return assignmentRepo.findByBookingPassenger_Passenger_User_UserId(userId
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerPackageRepository.findAll()
            Service->>Repository: BookingPassengerPackageRepository.findByBookingPassenger_Booking_BookingId()
            Service->>Repository: BookingPassengerPackageRepository.findByBookingPassenger_Passenger_User_UserId()
            Service->>Repository: BookingPassengerRepository.findByBookingBookingId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
