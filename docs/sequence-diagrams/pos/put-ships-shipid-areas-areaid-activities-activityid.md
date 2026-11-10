# PUT /ships/{shipId}/areas/{areaId}/activities/{activityId}

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
    Client->>Security: PUT /ships/{shipId}/areas/{areaId}/activities/{activityId}
    Note over Security,Controller: POS_MANAGE; active nonlocked account
    alt Authentication, authority or account status rejected
        Security-->>Client: 401 or 403
    else Authorized
        Security->>Controller: Bind parameters and validate request DTO
        Controller->>Service: assign()
        Note over Service,DB: REQUIRED transaction; JDBC and JPA share transaction; failures roll back mutations
        Service->>Repository: Lock ship-scoped physical area; enabled links require READY model
        Service->>Repository: Lock OnboardActivity; require cruise ship equals area ship and any specified deck or cabin deck equals area deck
        Service->>Repository: Omitted permissions means inheritance; explicit permissions must be ACTIVITY operations within source
        Service->>Repository: Upsert unique area/activity link; do not create activity or modify model geometry, cabin, deck or venue
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

Existing POS_MANAGE plus enabled/unlocked account, ship/model/deck/resource and permissions rules remain. V95 locks the area during enabled assignment: services cannot enter ACTIVITY_SPACE; activities require ACTIVITY_SPACE without enabled services. Incompatible writes return existing 409 DATA_INTEGRITY_VIOLATION and roll back. Disabled assignments remain configurable.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `authenticated (SecurityConfig anyRequest); no handler authority annotation` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as PosActivityController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PUT /ships/{shipId}/areas/{areaId}/activities/{activityId}
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
