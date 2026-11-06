# DELETE /ships/{shipId}/areas/{areaId}/staff/{userId}/resources/{resourceType}/{resourceId}

```mermaid
sequenceDiagram
    title DELETE /ships/{shipId}/areas/{areaId}/staff/{userId}/resources/{resourceType}/{resourceId} — PosStaffResourceController.remove
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as PosStaffResourceController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /ships/{shipId}/areas/{areaId}/staff/{userId}/resources/{resourceType}/{resourceId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke remove(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.remove()


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
