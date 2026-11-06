# Resource policy -> physical area -> employee POS operation

Source-reviewed 2026-10-30. Source files: ResourceOperationController/Service, PosController/Service/Repository, PosAccessService, PosActivityController/Service and reused ActivityService. This diagram describes the shared permission and access gates; individual API diagrams document business branches.

```mermaid
sequenceDiagram
    actor Manager as Activity/service manager
    actor Ops as POS manager
    actor Employee
    participant Security as JWT and method authorization
    participant Source as ResourceOperationService
    participant Config as PosService or PosActivityService
    participant Access as PosService and PosAccessService
    participant Business as ServiceRequestService or ActivityService
    participant Repository as JDBC and JPA repositories
    participant DB as PostgreSQL
    Manager->>Security: PUT resource permissions
    Security->>Source: Require active nonlocked ACTIVITY_SERVICE_MANAGER and matching UPDATE
    Source->>Repository: Validate resource and operation category
    Repository->>DB: Upsert ServiceOperationPolicy or ActivityOperationPolicy, actor and time
    DB-->>Source: Commit canonical set
    Ops->>Security: PUT area resource link, enabled true, omit permissions
    Security->>Config: Require POS_MANAGE and active account
    Config->>Repository: Lock ship-scoped physical FACILITY in READY model
    alt Service
        Repository->>DB: Require existing Service and DeckServiceAssignment
    else Onboard activity
        Repository->>DB: Lock activity; require same ship and any deck/cabin deck matches area
    end
    Config->>DB: Upsert unique area link with inherit_permissions true
    Note over Config,DB: Do not modify model coordinates, dimensions, deck, cabin or embedded metadata
    Ops->>Access: Configure terminal-area links, terminal caps and optional resource restrictions
    Access->>DB: Employee area assignment must reference active CruiseStaff; no global permission grants
    Employee->>Access: JWT plus device secret, selected area and cruise
    Access->>Repository: Lock terminal and area; validate secret, enabled links, READY model, employee and crew proof
    Access->>DB: Store session hash with employee, terminal, assignment, expiry
    Access-->>Employee: Session token fixed to selected area and cruise
    Employee->>Security: POS operation, JWT and X-POS-Session
    Security->>Access: Require active nonlocked POS_ACCESS employee
    Access->>Repository: Check session owner, lock terminal and area, re-read assignment and revocation/expiry after locks
    Repository->>DB: Read live source, optional area cap, terminal cap and optional resource override
    alt Disabled link/override, empty source, scope mismatch or required operation absent
        Access-->>Employee: 403; no business mutation
    else Effective capability within selected area, ship and cruise
        Access->>Business: Execute existing scoped service/activity operation
        Business->>Repository: Validate business state, capacity, booking and relevant locks
        Repository->>DB: Atomic persistence and constraints
        Note over Business,DB: Service usage consumes virtual booking/group allowance plus optional personal cap; no Payment collection. Activity operations reuse existing registration, attendance and lifecycle rules.
        DB-->>Employee: Commit and response; any exception rolls back
    end
    Manager->>Source: Replace or clear canonical permissions
    Source->>DB: Update source only, retain links and histories
    Note over Employee,DB: Next request re-evaluates live source; an open session cannot retain removed operations
```
