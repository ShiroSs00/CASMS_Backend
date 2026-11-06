# GET /pos/activities/{activityId}/participations

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
    Client->>Security: GET /pos/activities/{activityId}/participations
    Note over Security,Controller: POS_ACCESS; active nonlocked account
    alt Authentication, authority or account status rejected
        Security-->>Client: 401 or 403
    else Authorized
        Security->>Controller: Bind parameters and validate request DTO
        Controller->>Service: participations()
        Note over Service,DB: REQUIRED transaction; JDBC and JPA share transaction; failures roll back mutations
        Service->>Access: access(token, employee)
        Access->>Repository: Resolve session SHA-256 hash and employee ownership; lock terminal
        Repository->>DB: SELECT current session and terminal FOR UPDATE
        Access->>Repository: Lock physical area; require READY and enabled terminal-area link
        Repository->>DB: JOIN AreaStaffAssignment, CruiseStaff, Cruise; enabled grant, ACTIVE crew, eligible cruise and valid clock-time window
        Access->>Repository: Re-read session revocation and expiry after blocking access locks
        Access-->>Service: Selected area and cruise or 403; no global authority grants
        Service->>Repository: Require effective ACTIVITY_ATTENDANCE for activity in selected POS area and cruise
        Service->>Repository: Query Participation joined to Registration by exact onboard activity; map scoped records to ParticipationView
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
