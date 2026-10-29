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

    Note over Controller,Service: Source branch: if !List.of(ShoreExcursionStatus.PLANNED,ShoreExcursionStatus.OPEN,ShoreExcursionStatus.FULL,ShoreExcursionStatus.CLOSED
    Note over Controller,Service: Source branch: if excursion.getStatus(
    Note over Controller,Service: Source branch: if excursion.getStatus() == ShoreExcursionStatus.FULL
    Note over Controller,Service: Source branch: if maximumCapacity<allocated
    Note over Controller,Service: Source branch: if maximumCapacity==null||maximumCapacity<1||price==null||price.signum(
    Note over Controller,Service: Source branch: if registered >= excursion.getMaximumCapacity()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShoreExcursionRepository.lockById()
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
