# PATCH /cruise-plans/{cruiseId}/excursions/{id}/commercial

```mermaid
sequenceDiagram
    title PATCH /cruise-plans/{cruiseId}/excursions/{id}/commercial — CruiseScopedProgramsController.commercial
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /cruise-plans/{cruiseId}/excursions/{id}/commercial + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke commercial(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: excursions.updateCommercial()

    Note over Controller,Service: Source branch: if excursion.getStatus(
    Note over Controller,Service: Source branch: if excursion.getStatus() == ShoreExcursionStatus.FULL
    Note over Controller,Service: Source branch: if maximumCapacity < allocated
    Note over Controller,Service: Source branch: if registered >= excursion.getMaximumCapacity()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShoreExcursionRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws com.casms.common.exception.BusinessException; HTTP mapping requires exception-handler review
    end
```

## CASMS-75 behavior change

Commercial update calls shared ShoreExcursionService validation; same-stop overlap is permitted, while allocation, explicit price/currency, time/port/supplier and revision/editability checks remain. Full reviewed delta: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Existing static baseline above is not newly approved in its entirety.

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Service
    participant Repository
    participant DB as PostgreSQL
    Client->>Controller: Authorized request using existing route contract
    Controller->>Service: Validate program without exclusive venue rejection
    Service->>Repository: Retain scope, allocation and program checks
    Repository->>DB: Existing bound queries
    Service-->>Client: Existing response; errors preserve current mapping
```
