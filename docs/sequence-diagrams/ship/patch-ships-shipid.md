# PATCH /ships/{shipId}

```mermaid
sequenceDiagram
    title PATCH /ships/{shipId} — ShipController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /ships/{shipId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.update()

    Note over Controller,Service: Source branch: if commissionDate != null && commissionDate.isAfter(LocalDate.now())
    Note over Controller,Service: Source branch: if lengthMeters != null && (!Double.isFinite(lengthMeters) || lengthMeters <= 0
    Note over Controller,Service: Source branch: if request.getBeamMeters() != null) ship.setBeamMeters(request.getBeamMeters()
    Note over Controller,Service: Source branch: if request.getCommissionDate() != null
    Note over Controller,Service: Source branch: if request.getLengthMeters() != null) ship.setLengthMeters(request.getLengthMeters()
    Note over Controller,Service: Source branch: if request.getShipName() != null
    Note over Controller,Service: Source branch: if request.getTotalCapacity() != null
    Note over Controller,Service: Source branch: if request.getTotalCapacity() < 1
    Note over Controller,Service: Source branch: if request.getTotalDecks() != null
    Note over Controller,Service: Source branch: if request.getTotalDecks() < 1
    Note over Controller,Service: Source branch: if request.getTotalDecks() < configuredDeckCount
    Note over Controller,Service: Source branch: if shipName.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.sumCapacityByShipId()
            Service->>Repository: DeckRepository.countByShipShipIdAndServiceLevelFalse()
            Service->>Repository: ShipRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
