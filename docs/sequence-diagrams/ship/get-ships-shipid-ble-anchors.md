# GET /ships/{shipId}/ble-anchors

```mermaid
sequenceDiagram
    title GET /ships/{shipId}/ble-anchors — BleAnchorController.list
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BleAnchorController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /ships/{shipId}/ble-anchors + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke list(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.list()

    Note over Controller,Service: Source branch: if !ships.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId
    Note over Controller,Service: Source branch: if deckId != null && decks.findById(deckId).map(d -> !d.getShip().getShipId().equals(shipId)).orElse(true)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: DeckRepository.findById()
            Service->>Repository: LocationDeviceRepository.findByShipShipId()
            Service->>Repository: LocationDeviceRepository.findByShipShipIdAndDeckDeckId()
            Service->>Repository: ShipRepository.existsById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws ResourceNotFoundException; HTTP mapping requires exception-handler review
    end
```
