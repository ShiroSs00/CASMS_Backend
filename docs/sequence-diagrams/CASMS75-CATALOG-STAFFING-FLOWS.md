# CASMS-75 catalog and staffing flows

```mermaid
sequenceDiagram
    actor Client
    participant Auth as Security and method authorization
    participant API as Catalog controller or staffing service
    participant DB as PostgreSQL
    Client->>Auth: Bearer token and request
    Auth->>API: Active account, required authorities and role
    alt Labels and benefit reads
        API->>DB: Page stable keys with exact locale, language and Vietnamese fallback
        API-->>Client: 200 or 400 for invalid locale and pagination
    else Tour verification
        API->>DB: Lock candidate and validate port, supplier and human evidence
        alt Verified pair
            API->>DB: Record review in staging
            API-->>Client: 200 with staged candidate
        else Invalid input or state
            API-->>Client: 400 or 409
        end
    else Staffing submission or review
        API->>DB: Lock cruise and request, check actor scope and revision
        API->>DB: Check current service coverage and record decision atomically
        API-->>Client: Updated snapshot or error
    end
```

Catalog labels and benefit data require staff access. Draft quotas are proposals. Verification records the actor and evidence and rechecks an active Vietnamese port and verified active supplier. The staged verification and view never write live shore excursions.

Staffing advances from the assigned planner to HR, the assigned ship manager, the planner and OPS. HR controls staffing and duty writes. Database constraints and employee locks enforce active duty uniqueness and overlapping cruise windows. POS service grants and operations check the active service duty. Staffing changes invalidate approval and crew preparation. Readiness and the Ready fingerprint use the staffing request status and revision.
