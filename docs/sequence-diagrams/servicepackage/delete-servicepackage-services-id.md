# DELETE /servicepackage/services/{id}

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

The association never cascades deletion of a catalogue service. Remove deck assignments first. Existing SERVICE_DELETE_UNUSED permission remains unchanged.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ServicepackageController
    participant Service as ServicepackageService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: DELETE /servicepackage/services/{id}
    Note over Security,Controller: Handler permission hasAuthority('SERVICE_DELETE_UNUSED')
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: deleteService with bound parameters
        Controller->>Service: service.deleteService()
        Note over Service,DB: Transaction begins
        Service->>Repository: Load Service and check requests, packages and feedback
        Repository->>DB: SELECT service and references
        alt Service missing or existing business references
            Service-->>Controller: HTTP 404 or existing business-state error
        else No known business references
            Service->>Repository: delete Service
            Repository->>DB: DELETE subject to foreign keys
            alt DeckServiceAssignment references service
                DB-->>Service: Foreign key violation, rollback
                Service-->>Controller: DATA_INTEGRITY_VIOLATION HTTP 409
            else No references
                Note over Service,DB: Commit delete
            end
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 204 No Content
    end
```
