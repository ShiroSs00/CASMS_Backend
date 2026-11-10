# PUT /ships/{shipId}/areas/{areaId}/services/{serviceId}

Area staff and POS policy source-reviewed 2026-10-30. Paths are relative to `/api`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method authorization
    participant Controller as PosController
    participant Service as PosService
    participant Access as PosAccessService
    participant Ledger as ServiceRequestService and PosLedgerService
    participant Repository as JDBC and Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: PUT /ships/{shipId}/areas/{areaId}/services/{serviceId}
    Note over Security,Controller: hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked
    alt Missing authentication, authority or active account
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Bind parameters and validate DTO
        Controller->>Service: assign()
        Note over Service,DB: Class-level REQUIRED transaction, JDBC and JPA share one connection
        Service->>Service: Lock and validate the ship-scoped physical area in a READY model
        Service->>Service: Require the catalogue Service and an existing DeckServiceAssignment on this area deck
        Service->>Service: Upsert one AreaServiceAssignment per area and service; permission enum excludes global administration and payment
        Service->>Repository: Execute scoped queries and mutations described above
        Repository->>DB: Reads, locks, constraints and atomic persistence
        DB-->>Service: Result
        opt Invalid data, scope, proof, permission or state
            Service-->>Client: 400 DTO; 403 access; 404 missing scope; 409 conflict or ambiguous cruise; 422 business validation; 500 unexpected persistence failure
        end
        Note over Service,DB: Commit REQUIRED transaction on success; any failure rolls back the transaction
        Service-->>Controller: Response DTO after successful transaction
        Controller-->>Client: 200 response
    end
    Note over Service,DB: Resource permission change reviewed 2026-10-30. Source policy is mandatory; effective service operations equal source intersect optional area restriction intersect terminal cap intersect optional POS restriction. Missing POS override inherits; disabled override denies. Missing source grants nothing.
```

AssignmentInput.permissions is optional: null inherits live service policy; explicit empty set denies all. Explicit values must be SERVICE operations within the canonical source. Existing Service and DeckServiceAssignment are still required. The response returns current inherited permissions and inherited flag.

## CASMS-75 behavior change

Existing POS_MANAGE plus enabled/unlocked account, ship/model/deck/resource and permissions rules remain. V95 locks the area during enabled assignment: services cannot enter ACTIVITY_SPACE; activities require ACTIVITY_SPACE without enabled services. Incompatible writes return existing 409 DATA_INTEGRITY_VIOLATION and roll back. Disabled assignments remain configurable.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as PosController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PUT /ships/{shipId}/areas/{areaId}/services/{serviceId}
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: assign request
    Service->>Service: Retain POS checks; validate and lock physical area
    Service->>Repository: UPSERT assignment; trigger locks area and checks usage/service conflicts
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
