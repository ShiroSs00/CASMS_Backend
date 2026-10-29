# DELETE /ships/{shipId}/unused

```mermaid
sequenceDiagram
    title DELETE /ships/{shipId}/unused — ShipController.deleteUnused
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShipController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /ships/{shipId}/unused + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke deleteUnused(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.deleteUnused()

    Note over Controller,Service: Source branch: if !shipModelRepository.findByShipShipIdOrderByCreatedAtDesc(shipId).isEmpty(
    Note over Controller,Service: Source branch: if cruiseRepository.existsByShip(ship)
    Note over Controller,Service: Source branch: if deckRepository.countByShipShipId(shipId) > 0 || cabinRepository.countByShipShipId(shipId) > 0
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.countByShipShipId()
            Service->>Repository: CruiseRepository.existsByShip()
            Service->>Repository: DeckRepository.countByShipShipId()
            Service->>Repository: LocationDeviceRepository.findByShipShipId()
            Service->>Repository: ShipModelRepository.findByShipShipIdOrderByCreatedAtDesc()
            Service->>Repository: ShipRepository.delete()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
