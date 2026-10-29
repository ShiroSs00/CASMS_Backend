# POST /cruises/{cruiseId}/regulations

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/regulations — CruiseRegulationController.assign
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/regulations + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assign(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: cruiseRegulationService.assign()

    Note over Controller,Service: Source branch: if Boolean.FALSE.equals(verdict.getCanProceed())
    Note over Controller,Service: Source branch: if candidates.isEmpty()
    Note over Controller,Service: Source branch: if cruiseId == null || regulationId == null
    Note over Controller,Service: Source branch: if cruiseRegulationRepository.existsByCruise_CruiseIdAndRegulation_RegulationId(cruiseId, regulationId)
    Note over Controller,Service: Source branch: if hasConflictingValue
    Note over Controller,Service: Source branch: if regulation.getScope() != RegulationScope.CRUISE
    Note over Controller,Service: Source branch: if regulation.getScope() == RegulationScope.GLOBAL
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRegulationRepository.save()
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: OperationalRegulationRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
