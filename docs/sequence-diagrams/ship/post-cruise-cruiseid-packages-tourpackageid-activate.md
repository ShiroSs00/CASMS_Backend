# POST /cruise/{cruiseId}/packages/{tourPackageId}/activate

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/packages/{tourPackageId}/activate — CruiseController.activatePackage
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/packages/{tourPackageId}/activate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke activatePackage(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: packageService.activatePackage()

    Note over Controller,Service: Source branch: for var a : activities
    Note over Controller,Service: Source branch: for var e : excursions
    Note over Controller,Service: Source branch: for var s : services
    Note over Controller,Service: Source branch: if !mainflow.commercialBlockers(cruiseId).isEmpty()
    Note over Controller,Service: Source branch: if (physicalCapacity - booked - held) <= 0
    Note over Controller,Service: Source branch: if (pkg.getStartDate() != null && dep.isBefore(pkg.getStartDate())
    Note over Controller,Service: Source branch: if a.getOnboardActivity(
    Note over Controller,Service: Source branch: if cruise.getDepartureDatetime() != null && cruise.getReturnDatetime() != null
    Note over Controller,Service: Source branch: if cruise.getShip() != null
    Note over Controller,Service: Source branch: if cruise.getStatus() == CruiseStatus.CANCELLED || cruise.getStatus() == CruiseStatus.COMPLETED
    Note over Controller,Service: Source branch: if cruise.getStoppedAt() != null && (cruise.getStopScope() == null || "WHOLE_CRUISE".equalsIgnoreCase(cruise.getStopScope()
    Note over Controller,Service: Source branch: if ctp.isActive()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: CruiseTourPackageRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
