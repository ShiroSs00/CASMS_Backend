# GET /cruises/{cruiseId}/regulations/effective

```mermaid
sequenceDiagram
    title GET /cruises/{cruiseId}/regulations/effective — CruiseRegulationController.getEffectiveSnapshot
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseRegulationController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruises/{cruiseId}/regulations/effective + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getEffectiveSnapshot(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: cruiseRegulationService.getEffectiveSnapshot()

    Note over Controller,Service: Source branch: for OperationalRegulation gr : globalRegs
    Note over Controller,Service: Source branch: for ShipRegulation sr : shipRelations
    Note over Controller,Service: Source branch: if cruise.getShip() != null
    Note over Controller,Service: Source branch: if cruiseId == null
    Note over Controller,Service: Source branch: if isEffectiveAt(gr, effectiveAt) && !directIds.contains(gr.getRegulationId()) && !inheritedIds.contains(gr.getRegulationId())
    Note over Controller,Service: Source branch: if isEffectiveAt(r, effectiveAt) && !directIds.contains(r.getRegulationId())
    Note over Controller,Service: Source branch: if r.getEffectiveFrom().isAfter(at)
    Note over Controller,Service: Source branch: if r.getStatus() != RegulationStatus.ACTIVE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRegulationRepository.findByCruise_CruiseId()
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: OperationalRegulationRepository.findByScope()
            Service->>Repository: ShipRegulationRepository.findByShip_ShipId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
