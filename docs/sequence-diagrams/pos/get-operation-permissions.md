# GET /operation-permissions

Source-reviewed 2026-10-30 against ResourceOperationController.definitions, ResourceOperationService.definitions and PosPermission. Path is relative to `/api`.

```mermaid
sequenceDiagram
    actor Client
    participant Security as JWT and method authorization
    participant Controller as ResourceOperationController
    participant Service as ResourceOperationService
    participant Codes as PosPermission enum
    Client->>Security: GET /operation-permissions
    Note over Security,Controller: SERVICE_READ or ACTIVITY_READ or POS_MANAGE; active nonlocked account
    alt Authentication, authority or account status rejected
        Security-->>Client: 401 or 403
    else Authorized
        Security->>Controller: Bind optional SERVICE or ACTIVITY resourceType query enum
        alt Invalid query enum
            Controller-->>Client: 400 via MethodArgumentTypeMismatch handler
        else Valid or omitted filter
            Controller->>Service: definitions(resourceType)
            Note over Service: Class-level REQUIRED transaction; no repository calls, database queries or mutations in this method
            Service->>Codes: Enumerate implemented scoped operation codes
            Codes-->>Service: Code, resourceType and category
            Service->>Service: Apply optional resourceType filter and map PermissionDefinition
            Note over Service,Codes: Exclude catalogue administration and real-money payment permissions
            Service-->>Controller: Classified definitions
            Controller-->>Client: 200 ApiResponse
        end
    end
```
