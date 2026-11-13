# POST /employees/imports/{batchId}/confirm

```mermaid
sequenceDiagram
    title POST /employees/imports/{batchId}/confirm — EmployeeImportController.confirm
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as EmployeeImportController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /employees/imports/{batchId}/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirm(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.confirm()

    Note over Controller,Service: Source branch: for int number : pending
    Note over Controller,Service: Source branch: if error != null
    Note over Controller,Service: Source branch: if requireBatch(batch, actor)) return read(batch, actor
    Note over Controller,Service: Source branch: if rows.isEmpty()
    Note over Controller,Service: Source branch: if updated == 1) audit(actor.getUserId(), batch, "EMPLOYEE_IMPORT_CONFIRMED"
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
