# PUT /ship-types/{typeId}/model

See [the WebAdmin contract](../../ship-redesign/CASMS_SHIP_BE_HANDOFF_FOR_FE.md) for request parameters, response fields, and errors.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method policy
    participant API as Ship API
    participant DB as PostgreSQL
    Client->>Security: PUT /ship-types/{typeId}/model
    Security->>API: ADMIN
    API->>API: Lock type and validate stored version
    API->>DB: Change the type asset pointer
    DB-->>API: Domain result
    API-->>Client: 200 ShipType or 409 design mismatch
```
