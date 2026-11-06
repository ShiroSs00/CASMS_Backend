# PATCH /decks/{deckId}

```mermaid
sequenceDiagram
    title PATCH /decks/{deckId} — DeckController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DeckController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /decks/{deckId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.update()

    Note over Controller,Service: Source branch: if deck.isServiceLevel()
    Note over Controller,Service: Source branch: if request.getDeckName() != null
    Note over Controller,Service: Source branch: if request.getDeckNumber(
    Note over Controller,Service: Source branch: if request.getDeckNumber() < 1
    Note over Controller,Service: Source branch: if request.getDeckNumber() > deckNumberLimit
    Note over Controller,Service: Source branch: if request.getModelPositionX(
    Note over Controller,Service: Source branch: if request.getPriceMultiplier() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: DeckRepository.existsByShipShipIdAndDeckNumber()
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
