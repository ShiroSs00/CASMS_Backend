# Import decks and assign catalogue services

Reviewed against the changed import, asset, deck and assignment services on 2026-10-29. All HTTP paths use the `/api` context. Each endpoint retains its own permission checks; the client may use separate ship-manager and service-manager identities.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method permissions
    participant API as ShipModelController and DeckServiceAssignmentController
    participant Import as ShipModelImportService
    participant Assignment as DeckServiceAssignmentService
    participant Repository as JPA repositories and asset store
    participant DB as PostgreSQL
    Note over Client,API: Prerequisite ZIP upload and DRAFT model registration, existing catalogue service
    Client->>Security: POST ship model imports with SHIP_UPDATE
    Security->>API: importStructure
    API->>Import: importModel(shipId, modelId, actor)
    Import->>DB: Independently commit RUNNING import job
    Import->>Repository: Lock ship, read immutable manifest and GLB JSON
    Repository->>DB: Ship lock and Large Object reads
    Import->>Repository: Upsert passenger Decks, technical Decks and model mappings
    Repository->>DB: Store UUIDs, source metadata, level flag and elevation
    Note over Import: Ignore model service and area objects, retain existing cabin import
    alt Persistence fails
        Import->>DB: Roll back all structure writes, persist FAILED job separately
        API-->>Client: Mapped error response
    else Structure transaction completes
        Import->>DB: Commit structure with SUCCEEDED or PARTIAL job
        API-->>Client: 201 job result
        Client->>Security: GET decks by ship, requires DECK_READ
        Security->>API: DeckController listByShip
        API->>Repository: Read physical deck list
        Repository->>DB: SELECT Decks including service levels
        API-->>Client: Deck UUIDs and metadata
        Client->>Security: PUT deck services serviceId, DECK_READ and SERVICE_UPDATE
        Security->>API: assign
        API->>Assignment: assign(deckId, serviceId)
        Assignment->>Repository: Lock Deck and existing Service, resolve pair
        Repository->>DB: Read or insert unique DeckServiceAssignment, commit
        API-->>Client: 200 assignment DTO
        opt Reimport same DRAFT model or import a new model version
            Client->>API: Import with authorized ship-manager identity
            API->>Import: importModel
            Import->>DB: Reuse deck identities, preserve manual assignment rows
        end
        Client->>Security: GET deck services, DECK_READ and SERVICE_READ
        Security->>API: list
        API->>Assignment: list(deckId)
        Assignment->>Repository: Read assignments with service data
        Repository->>DB: Read-only query
        API-->>Client: 200 assignments preserved across reimport
    end
```

Foreign keys protect referenced decks and services. Removing an assignment deletes only the relationship. This flow does not activate services, create prices or change model publication rules.
