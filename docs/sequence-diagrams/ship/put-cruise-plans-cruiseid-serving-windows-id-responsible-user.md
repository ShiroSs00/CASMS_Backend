# PUT /cruise-plans/{cruiseId}/serving-windows/{id}/responsible-user

```mermaid
sequenceDiagram
    title PUT /cruise-plans/{cruiseId}/serving-windows/{id}/responsible-user — CruiseScopedProgramsController.assignServiceResponsible
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /cruise-plans/{cruiseId}/serving-windows/{id}/responsible-user + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignServiceResponsible(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Note over Controller: Handler contains inline logic; no application service invocation detected


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body

        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#assign-responsible-user-after-scheduling). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Scheduling and staff assignment occur after catalog creation. Responsible user must be ACTIVE and actively assigned to the same cruise; null clears it. Writes use editable-plan and If-Match checks and share resource/revision rollback. Schedule read views include responsibleUserId. Existing service-window time/capacity/ship/supplier checks remain in force.
