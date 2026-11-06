# GET /pos/requests

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
    Client->>Security: GET /pos/requests
    Note over Security,Controller: hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked
    alt Missing authentication, authority or active account
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Bind parameters and validate DTO
        Controller->>Service: requests()
        Note over Service,DB: Class-level REQUIRED transaction, JDBC and JPA share one connection
        Service->>Repository: Resolve session hash and employee ownership; lock terminal and re-read session
        Repository->>DB: SELECT PosSession; PosTerminal FOR UPDATE
        Service->>Access: sessionAssignment(proofId, terminal, employee)
        Access->>Repository: Lock area in terminal ship; require enabled PosTerminalArea and READY model
        Repository->>DB: SELECT area FOR UPDATE and current enabled link
        Access->>DB: JOIN AreaStaffAssignment, CruiseStaff and Cruise; require employee, ACTIVE crew, enabled grant and valid clock-time window
        Access-->>Service: Exact selected area and cruise proof, or 403
        Service->>Repository: Re-read session revocation and expiry after all blocking access locks
        Note over Service: Area grant changes cannot revive an old revoked session; no global permission elevation
        Service->>Service: Require terminal SERVICE_REQUEST_READ
        Service->>Service: Filter by selected cruise, ship, ACTIVE service, current deck assignment and source READ and enabled parent plus optional POS READ restrictions BEFORE the 200-row limit
        Service->>Service: Return only requests allowed by the effective permission intersection
        Service->>Repository: Execute scoped queries described above
        Repository->>DB: Scoped SELECT and required access/account locks; no business mutation
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

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#prepaid-reservation-and-completion). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Request reads expose the purchase/usage snapshot and fulfilled quantity. Reservation checks explicit fare or legacy INCLUDED service grants for paid services, plus used/pending period quota. Completion writes usage once in the same transaction; cancellation releases pending quota and reverses linked POS charge where applicable.
