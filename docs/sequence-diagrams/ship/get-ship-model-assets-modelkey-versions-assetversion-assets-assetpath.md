# GET /ship-model-assets/{modelKey}/versions/{assetVersion}/assets/{*assetPath}

See [the WebAdmin contract](../../ship-redesign/CASMS_SHIP_BE_HANDOFF_FOR_FE.md) for request parameters, response fields, and errors.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method policy
    participant API as Ship API
    participant DB as PostgreSQL
    Client->>Security: GET /ship-model-assets/{modelKey}/versions/{assetVersion}/assets/{*assetPath}
    Security->>API: ADMIN with SHIP_MODEL_READ
    API->>API: Authorize requested asset against pinned manifest
    API->>DB: Stream stored blob by exact version
    DB-->>API: Domain result
    API-->>Client: 200 GLB or JSON, 404 if asset is unavailable
```
