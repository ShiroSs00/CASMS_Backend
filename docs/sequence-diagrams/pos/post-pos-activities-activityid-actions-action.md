# POST /pos/activities/{activityId}/actions/{action}

Source-reviewed 2026-10-30 against PosActivityService and reused ActivityService behavior. Paths are relative to `/api`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method authorization
    participant Controller as PosActivityController
    participant Service as PosActivityService
    participant Access as PosService and PosAccessService
    participant Business as ActivityService
    participant Repository as JDBC and Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: POST /pos/activities/{activityId}/actions/{action}
    Note over Security,Controller: POS_ACCESS; active nonlocked account
    alt Authentication, authority or account status rejected
        Security-->>Client: 401 or 403
    else Authorized
        Security->>Controller: Bind parameters and validate request DTO
        Controller->>Service: lifecycle()
        Note over Service,DB: REQUIRED transaction; JDBC and JPA share transaction; failures roll back mutations
        Service->>Access: access(token, employee)
        Access->>Repository: Resolve session SHA-256 hash and employee ownership; lock terminal
        Repository->>DB: SELECT current session and terminal FOR UPDATE
        Access->>Repository: Lock physical area; require READY and enabled terminal-area link
        Repository->>DB: JOIN AreaStaffAssignment, CruiseStaff, Cruise; enabled grant, ACTIVE crew, eligible cruise and valid clock-time window
        Access->>Repository: Re-read session revocation and expiry after blocking access locks
        Access-->>Service: Selected area and cruise or 403; no global authority grants
        Service->>Repository: Lock target OnboardActivity; re-evaluate live venue, selected cruise and effective ACTIVITY_LIFECYCLE
        Service->>Business: Delegate ActivityService.lifecycle; open validates schedule, start requires open or full and start time reached, complete requires in-progress, cancel rejects completed or already cancelled
        Service->>Repository: JPA persists state in the same REQUIRED transaction; no money collection
        Repository->>DB: Scoped reads, row locks and writes described above
        DB-->>Repository: Constraint-checked result
        opt Validation, scope or business failure
            Service-->>Client: 400 DTO or binding; 403 scope; 404 resource; 422 business validation; unexpected persistence failure 500
        end
        Note over Service,DB: Commit successful mutation; rollback on exception
        Service-->>Controller: DTO or completed operation
        Controller-->>Client: 200
    end
```

## CASMS-75 behavior change

Same location/port-call and overlapping time alone no longer constitute a conflict. Existing authorization, editable-plan/revision guards on cruise-scoped routes, ordered times, cruise/port/tender bounds, allocation/capacity, currency, supplier, venue and lifecycle checks remain. Activity scheduleConflicts checks existence then returns zero for the removed exclusive-venue rule. Operations completeness still requires a dedicated area and planned shore destination. This addendum supersedes obsolete exclusive-venue branches in the historical static trace above; unrelated branches keep their previous review status.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `authenticated (SecurityConfig anyRequest); no handler authority annotation` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as PosActivityController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: POST /pos/activities/{activityId}/actions/{action}
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: lifecycle request
    Service->>Service: Run existing program validation without exclusive same-venue overlap rejection
    Service->>Repository: Existing bound repository reads/writes; preserve transactional allocation and time guards
    Repository->>DB: Read or mutate in declared transaction
    alt Validation, authorization, revision or integrity failure
        Service-->>Client: Existing 400 / 401 / 403 / 404 / 409 / 422; mutation rolls back
    else Valid operation
        DB-->>Service: Current result / committed mutation
        Service-->>Controller: Existing DTO or documented new program response
        Controller-->>Client: HTTP success
    end
```
