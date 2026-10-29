# POST /ships/{shipId}/regulations

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/regulations — ShipRegulationController.assign
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/regulations + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assign(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: shipRegulationService.assign()

    Note over Controller,Service: Source branch: if Boolean.FALSE.equals(verdict.getCanProceed())
    Note over Controller,Service: Source branch: if candidates.isEmpty()
    Note over Controller,Service: Source branch: if hasConflictingValue
    Note over Controller,Service: Source branch: if regulation.getScope() != RegulationScope.SHIP
    Note over Controller,Service: Source branch: if regulation.getScope() == RegulationScope.GLOBAL
    Note over Controller,Service: Source branch: if shipId == null || regulationId == null
    Note over Controller,Service: Source branch: if shipRegulationRepository.existsByShip_ShipIdAndRegulation_RegulationId(shipId, regulationId)
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OperationalRegulationRepository.findById()
            Service->>Repository: ShipRegulationRepository.save()
            Service->>Repository: ShipRepository.findById()
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
