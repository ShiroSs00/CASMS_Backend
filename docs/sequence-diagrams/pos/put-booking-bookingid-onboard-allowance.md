# PUT /booking/{bookingId}/onboard-allowance

Area staff and POS policy source-reviewed 2026-10-30. Paths are relative to `/api`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method authorization
    participant Controller as PosController
    participant Service as OnboardAllowanceService
    participant Access as PosAccessService
    participant Ledger as ServiceRequestService and PosLedgerService
    participant Repository as JDBC and Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: PUT /booking/{bookingId}/onboard-allowance
    Note over Security,Controller: hasAuthority('POS_MANAGE') and principal.enabled and principal.accountNonLocked
    alt Missing authentication, authority or active account
        Security-->>Client: 401 or 403
    else Authorized request
        Security->>Controller: Bind parameters and validate DTO
        Controller->>Service: allocate()
        Note over Service,DB: Class-level REQUIRED transaction, JDBC and JPA share one connection
        Service->>Service: Take per-booking PostgreSQL transaction advisory lock to serialize initial account provisioning
        Service->>Service: Require confirmed booking on PUBLISHED or IN_PROGRESS cruise and distinct active booking passengers in member caps
        Service->>Service: Create or reuse the shared settlementOnboardAccount; lock existing account; require matching cruise and active account
        Service->>Service: Reject a group allocation or member cap below existing virtual usage
        Service->>Service: Create missing memberships; preserve revoked memberships; update explicit individual caps, retaining historical rows
        Service->>Service: Save allocation audit and shared allowance in one transaction; do not create Payment or money movement
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
