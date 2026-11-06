# DELETE /ships/{shipId}/areas/{areaId}/activities/{activityId}

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
    Client->>Security: DELETE /ships/{shipId}/areas/{areaId}/activities/{activityId}
    Note over Security,Controller: POS_MANAGE; active nonlocked account
    alt Authentication, authority or account status rejected
        Security-->>Client: 401 or 403
    else Authorized
        Security->>Controller: Bind parameters and validate request DTO
        Controller->>Service: unassign()
        Note over Service,DB: REQUIRED transaction; JDBC and JPA share transaction; failures roll back mutations
        Service->>Repository: Lock ship-scoped physical area; delete AreaActivityAssignment
        Service->>Repository: Cascade child POS restriction rows only; retain activity, registrations and attendance history
        Repository->>DB: Scoped reads, row locks and writes described above
        DB-->>Repository: Constraint-checked result
        opt Validation, scope or business failure
            Service-->>Client: 400 DTO or binding; 403 scope; 404 resource; 422 business validation; unexpected persistence failure 500
        end
        Note over Service,DB: Commit successful mutation; rollback on exception
        Service-->>Controller: DTO or completed operation
        Controller-->>Client: 204
    end
```
