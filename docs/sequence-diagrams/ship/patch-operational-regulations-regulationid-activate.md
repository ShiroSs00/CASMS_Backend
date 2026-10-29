# PATCH /operational-regulations/{regulationId}/activate

```mermaid
sequenceDiagram
    title PATCH /operational-regulations/{regulationId}/activate — OperationalRegulationController.activate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as OperationalRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /operational-regulations/{regulationId}/activate + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke activate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: regulationService.activateRegulation()

    Note over Controller,Service: Source branch: if Boolean.FALSE.equals(verdict.getCanProceed())
    Note over Controller,Service: Source branch: if candidates.isEmpty()
    Note over Controller,Service: Source branch: if effectiveFrom == null
    Note over Controller,Service: Source branch: if hasConflictingValue
    Note over Controller,Service: Source branch: if regulation.getEffectiveFrom().isAfter(now)
    Note over Controller,Service: Source branch: if regulation.getEffectiveTo(
    Note over Controller,Service: Source branch: if regulationId == null
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OperationalRegulationRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
