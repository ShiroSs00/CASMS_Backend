# PUT /shore-excursions/{id}

```mermaid
sequenceDiagram
    title PUT /shore-excursions/{id} — ShoreExcursionController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShoreExcursionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /shore-excursions/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: shoreExcursionService.update()

    Note over Controller,Service: Source branch: if excursion.getStatus(
    Note over Controller,Service: Source branch: if excursion.getStatus() == ShoreExcursionStatus.FULL
    Note over Controller,Service: Source branch: if registered >= excursion.getMaximumCapacity()
    Note over Controller,Service: Source branch: if request.maximumCapacity()!=null&&request.maximumCapacity()<allocated
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
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
