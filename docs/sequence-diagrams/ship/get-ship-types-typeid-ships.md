# GET /ship-types/{typeId}/ships

See [the WebAdmin contract](../../ship-redesign/CASMS_SHIP_BE_HANDOFF_FOR_FE.md) for request parameters, response fields, and errors.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method policy
    participant API as Ship API
    participant DB as PostgreSQL
    Client->>Security: GET /ship-types/{typeId}/ships
    Security->>API: ADMIN or scoped SHIP_MANAGER with SHIP_READ
    API->>API: Resolve visible type and filter vessels by scope
    API->>DB: Read matching ships with paging and count
    DB-->>API: Domain result
    API-->>Client: 200 Page of ShipResponse
```

The ShipResponse returns the stored commissionDate for newly provisioned typed ships.
