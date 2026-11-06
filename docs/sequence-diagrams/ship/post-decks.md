# POST /decks

```mermaid
sequenceDiagram
    title POST /decks — DeckController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DeckController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /decks + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if deckNumber < 1
    Note over Controller,Service: Source branch: if deckNumber == null
    Note over Controller,Service: Source branch: if deckNumber > deckNumberLimit
    Note over Controller,Service: Source branch: if deckRepository.existsByShipShipIdAndDeckNumber(ship.getShipId(), deckNumber)
    Note over Controller,Service: Source branch: if request.getModelPositionX(
    Note over Controller,Service: Source branch: while deckNumber <= deckNumberLimit && configured.contains(deckNumber)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: DeckRepository.existsByShipShipIdAndDeckNumber()
            Service->>Repository: DeckRepository.findByShipShipIdOrderByDeckNumber()
            Service->>Repository: DeckRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
