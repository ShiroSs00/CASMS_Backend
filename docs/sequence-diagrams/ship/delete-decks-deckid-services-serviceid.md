# DELETE /decks/{deckId}/services/{serviceId}

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

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
        Controller->>Service: service.unassign()
        Note over Service,DB: Transaction begins
        Service->>Repository: DeckRepository.findByIdForUpdate and ServiceRepository.existsById
        Repository->>DB: Lock deck and check service existence
        alt Parent missing
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
