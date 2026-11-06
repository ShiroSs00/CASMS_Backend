# PUT /servicepackage/package-assignments/{bookingPassengerId}

```mermaid
sequenceDiagram
    title PUT /servicepackage/package-assignments/{bookingPassengerId} — ServicepackageController.changePackage
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /servicepackage/package-assignments/{bookingPassengerId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke changePackage(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.changePackage()

    Note over Controller,Service: Source branch: if bp.getBooking().getBookingStatus() != BookingStatus.Draft
    Note over Controller,Service: Source branch: if p.getStartDate() != null && departure.isBefore(p.getStartDate()
    Note over Controller,Service: Source branch: if p.getStatus() != TourPackageStatus.ACTIVE
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws AccessDeniedException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
