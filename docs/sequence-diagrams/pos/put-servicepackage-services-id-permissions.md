# PUT /servicepackage/services/{id}/permissions

Source-reviewed 2026-10-30 against ResourceOperationService and reused ActivityService behavior. Paths are relative to `/api`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method authorization
    participant Controller as ResourceOperationController
    participant Service as ResourceOperationService
    participant Access as PosService and PosAccessService
    participant Business as ActivityService
    participant Repository as JDBC and Spring Data repositories
    participant DB as PostgreSQL
    Client->>Security: PUT /servicepackage/services/{id}/permissions
    Note over Security,Controller: ROLE_ACTIVITY_SERVICE_MANAGER and SERVICE_UPDATE; active nonlocked account
    alt Authentication, authority or account status rejected
        Security-->>Client: 401 or 403
    else Authorized
        Security->>Controller: Bind parameters and validate request DTO
        Controller->>Service: put()
        Note over Service,DB: REQUIRED transaction; JDBC and JPA share transaction; failures roll back mutations
        Service->>Repository: Require existing Service; reject non-service operation codes
        Service->>Repository: Upsert ServiceOperationPolicy with replacement set, updated_by_user_id and updated_at
        Service->>Repository: Existing area and POS restrictions remain; new source is evaluated live on the next operation
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
