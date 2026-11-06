# DELETE /pos-terminals/{terminalId}/areas/{areaId}/services/{serviceId}

Area staff and POS policy source-reviewed 2026-10-30. Paths are relative to `/api`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method authorization
    participant Controller as PosAssignmentController
    participant Service as PosAccessService
    participant Access as PosAccessService
    participant Ledger as ServiceRequestService and PosLedgerService
    participant Repository as JDBC and Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: DELETE /pos-terminals/{terminalId}/areas/{areaId}/services/{serviceId}
    Note over Security,Controller: hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked
    alt Missing authentication, authority or active account
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Bind parameters and validate DTO
        Controller->>Service: removePolicy()
        Note over Service,DB: Class-level REQUIRED transaction, JDBC and JPA share one connection
        Service->>Service: Lock terminal and area; upsert an explicit disabled POS-area-service override
        Service->>Service: Next operation observes disabled override and denies access; retain catalogue Service, PosSale, Charge and ledger history
        Service->>Repository: Execute scoped queries and mutations described above
        Repository->>DB: Reads, locks, constraints and atomic persistence
        DB-->>Service: Result
        opt Invalid data, scope, proof, permission or state
            Service-->>Client: 400 DTO; 403 access; 404 missing scope; 409 conflict or ambiguous cruise; 422 business validation; 500 unexpected persistence failure
        end
        Note over Service,DB: Commit REQUIRED transaction on success; any failure rolls back the transaction
        Service-->>Controller: Response DTO after successful transaction
        Controller-->>Client: 204 response
    end
    Note over Service,DB: Resource permission change reviewed 2026-10-30. Source policy is mandatory; effective service operations equal source intersect optional area restriction intersect terminal cap intersect optional POS restriction. Missing POS override inherits; disabled override denies. Missing source grants nothing.
```
