# POST /ship-model-assets/{modelKey}/{assetVersion}/register

Deck persistence change reviewed against implementation on 2026-10-29. Paths are relative to `/api`.

Registration preserves existing authority checks; manifest provisioning checks SHIP_CREATE for new ships and SHIP_UPDATE for existing ones. Technical levels do not inflate the passenger deckCount comparison.

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security and method authorization
    participant Controller as ShipModelController
    participant Service as ShipModelAssetService
    participant Repository as Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /ship-model-assets/{modelKey}/{assetVersion}/register
    Note over Security,Controller: Handler permission hasAuthority('SHIP_MODEL_CREATE') and (hasAuthority('SHIP_CREATE') or hasAuthority('SHIP_UPDATE'))
    alt Caller lacks required authority
        Security-->>Client: Denied by filter or method security
    else Authorized request
        Security->>Controller: registerFromManifest with bound parameters
        Controller->>Service: assetService.registerFromManifest()
        Note over Service,DB: Transaction begins
        Service->>DB: Read uploaded manifest and acquire manifest-identity advisory lock
        Service->>Repository: Resolve or lock target Ship
        Repository->>DB: SELECT Ship and existing business configuration
        Service->>Repository: countByShipShipIdAndServiceLevelFalse and configured cabin capacity
        Repository->>DB: Count passenger decks separately from technical levels
        Service->>Service: Validate source identity and complete metadata when provisioning a new Ship
        Service->>Repository: Save Ship metadata and register DRAFT model version
        Repository->>DB: INSERT or UPDATE with unique identity checks, commit
        opt Bundle missing, duplicate version or conflicting manifest identity
            Service-->>Controller: HTTP 404, 409 or validation error, rollback
        end
        Service-->>Controller: Result after transaction completion
        Controller-->>Client: 201 ApiResponse of registration result
    end
```
