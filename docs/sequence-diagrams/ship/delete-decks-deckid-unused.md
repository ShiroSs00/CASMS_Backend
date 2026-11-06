# DELETE /decks/{deckId}/unused

```mermaid
sequenceDiagram
    title DELETE /decks/{deckId}/unused — DeckController.deleteUnused
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DeckController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /decks/{deckId}/unused + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke deleteUnused(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.deleteUnused()

    Note over Controller,Service: Source branch: if cabinRepository.existsByDeckDeckId(deckId)
    Note over Controller,Service: Source branch: if shipModelObjectRepository.existsByDeckDeckId(deckId
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CabinRepository.existsByDeckDeckId()
            Service->>Repository: DeckRepository.delete()
            Service->>Repository: LocationDeviceRepository.existsByDeckDeckId()
            Service->>Repository: ShipModelObjectRepository.existsByDeckDeckId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
