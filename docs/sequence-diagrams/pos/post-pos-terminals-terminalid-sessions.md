# POST /pos-terminals/{terminalId}/sessions

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
    Client->>Security: POST /pos-terminals/{terminalId}/sessions
    Note over Security,Controller: hasAuthority('POS_ACCESS') and principal.enabled and principal.accountNonLocked
    alt Missing authentication, authority or active account
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Bind parameters and validate DTO
        Controller->>Service: login()
        Note over Service,DB: Class-level REQUIRED transaction, JDBC and JPA share one connection
        Service->>Service: Require active staff with POS_ACCESS; lock terminal and verify enabled state and BCrypt device secret
        Service->>Service: Resolve requested areaId or device default; lock published physical area on same ship and require enabled PosTerminalArea link
        Service->>Service: Find current enabled AreaStaffAssignment for employee and optional cruiseId; require ACTIVE CruiseStaff, valid access dates and eligible cruise status
        Service->>Service: No matching assignment returns 403; ambiguous selection returns 409 requiring cruiseId
        Service->>Service: Generate random session token; store SHA-256 hash, employee, terminal, staff assignment and expiry
        Service->>Service: Return selected area, cruiseId and staffAssignmentId; session permissions remain outside employee JWT
        Service->>Repository: Execute scoped queries and mutations described above
        Repository->>DB: Reads, locks, constraints and atomic persistence
        DB-->>Service: Result
        opt Invalid data, scope, proof, permission or state
            Service-->>Client: 400 DTO; 403 access; 404 missing scope; 409 conflict or ambiguous cruise; 422 business validation; 500 unexpected persistence failure
        end
        Note over Service,DB: Commit REQUIRED transaction on success; any failure rolls back the transaction
        Service-->>Controller: Response DTO after successful transaction
        Controller-->>Client: 201 response
    end
    Note over Service,DB: Resource permission change reviewed 2026-10-30. Source policy is mandatory; effective service operations equal source intersect optional area restriction intersect terminal cap intersect optional POS restriction. Missing POS override inherits; disabled override denies. Missing source grants nothing.
```
