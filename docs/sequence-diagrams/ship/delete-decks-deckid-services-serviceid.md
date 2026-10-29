# DELETE /decks/{deckId}/services/{serviceId}

CASMS-90 reviewed against implementation on 2026-10-29. Paths are relative to `/api`; optional `shipId` scopes the physical deck.

Idempotent when the assignment is absent and both parents still exist.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as DeckServiceAssignmentController
    participant Service as DeckServiceAssignmentService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: DELETE /decks/{deckId}/services/{serviceId}
    Note over Security,Controller: Handler permission hasAuthority('DECK_READ') and hasAuthority('SERVICE_UPDATE')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: unassign with bound parameters
        Note over Controller: Invalid path or query UUID returns 400 before service call
        Controller->>Service: unassign(deckId, serviceId, shipId)
        Note over Service,DB: Transaction begins
        Service->>Repository: DeckRepository.findByIdForUpdate
        Repository->>DB: SELECT deck FOR UPDATE
        opt Explicit shipId supplied
            Service->>Repository: ShipRepository.existsById(shipId)
            Repository->>DB: SELECT ship existence
            Service->>Service: Compare deck owning ship with requested shipId, reject mismatch with 404
        end
        Service->>Repository: ServiceRepository.findByIdForUpdate
        Repository->>DB: SELECT existing service FOR UPDATE
        alt Parent missing or deck outside requested ship
            Service-->>Controller: ResourceNotFoundException, rollback, HTTP 404
        else Parents exist
            Service->>Repository: findByDeckDeckIdAndServiceServiceId
            Repository->>DB: SELECT assignment
            opt Assignment exists
                Service->>Repository: delete assignment
                Repository->>DB: DELETE DeckServiceAssignment
            end
            Note over Service,DB: Commit, no catalogue service or deck is deleted
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 204 No Content
    end
```
