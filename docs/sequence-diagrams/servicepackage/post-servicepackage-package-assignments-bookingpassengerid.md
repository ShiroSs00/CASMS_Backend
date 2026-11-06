# POST /servicepackage/package-assignments/{bookingPassengerId}

```mermaid
sequenceDiagram
    title POST /servicepackage/package-assignments/{bookingPassengerId} — ServicepackageController.assignPackage
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/package-assignments/{bookingPassengerId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignPackage(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.assignPackage()

    Note over Controller,Service: Source branch: if assignmentRepo.existsById(bpId)
    Note over Controller,Service: Source branch: if bp.getBooking().getBookingStatus() != BookingStatus.Draft
    Note over Controller,Service: Source branch: if p.getStartDate() != null && departure.isBefore(p.getStartDate()
    Note over Controller,Service: Source branch: if p.getStatus() != TourPackageStatus.ACTIVE
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerPackageRepository.existsById()
            Service->>Repository: BookingPassengerPackageRepository.save()
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
