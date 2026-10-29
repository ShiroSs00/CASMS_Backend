# PUT /decks/{deckId}/services/{serviceId}

CASMS-90 reviewed against implementation on 2026-10-29. Paths are relative to `/api`; optional `shipId` scopes the physical deck.

No request body. Uses an existing catalogue service only. Does not change model state, create a service, or enable sales. Deck row lock serializes retries and concurrent assignments; the unique pair constraint is the database backstop.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckServiceAssignmentController
    participant Service as DeckServiceAssignmentService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: PUT /decks/{deckId}/services/{serviceId}
    Note over Security,Controller: Handler permission hasAuthority('DECK_READ') and hasAuthority('SERVICE_UPDATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: assign with bound parameters
        Note over Controller: Invalid path or query UUID returns 400 before service call
        Controller->>Service: assign(deckId, serviceId, shipId)
        Note over Service,DB: Transaction begins
        Service->>Repository: DeckRepository.findByIdForUpdate
        Repository->>DB: SELECT deck FOR UPDATE
        opt Explicit shipId supplied
            Service->>Repository: ShipRepository.existsById(shipId)
            Repository->>DB: SELECT ship existence
            Service->>Service: Compare deck owning ship with requested shipId, reject mismatch with 404
        end
        Service->>Repository: ServiceRepository.findByIdForUpdate
        Repository->>DB: SELECT existing catalogue service FOR UPDATE
        DB-->>Repository: Existing parent rows or no row
        alt Deck, requested ship or service missing, or deck outside ship scope
            Service-->>Controller: ResourceNotFoundException, rollback, HTTP 404
        else Parents exist
            Service->>Repository: findByDeckDeckIdAndServiceServiceId
            Repository->>DB: SELECT assignment by unique pair
            alt Pair already assigned
                DB-->>Service: Return existing assignment and original ID
            else New pair
                Service->>Repository: saveAndFlush assignment
                Repository->>DB: INSERT DeckServiceAssignment, unique pair and foreign keys
                DB-->>Service: Persisted assignment
            end
            Note over Service,DB: Commit before returning DTO; constraint failure rolls back with HTTP 409
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 200 ApiResponse
    end
```
