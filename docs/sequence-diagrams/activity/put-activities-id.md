# PUT /activities/{id}

```mermaid
sequenceDiagram
    title PUT /activities/{id} — ActivityController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /activities/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.updateActivity()


        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#activity-catalog-itinerary-and-occurrence). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Catalog and timed occurrences share storage but have explicit catalogEntry identity. Catalog POST rejects schedule fields. Scheduling copies metadata and retains sourceActivityId. Operational activity reads return catalogEntry/sourceActivityId/category/responsibleUserId. Catalog lifecycle/registration is rejected; source deletion is protected. Cruise-scoped reads/writes require the existing cruise access and revision guards.

## CASMS-75 behavior change

Same location/port-call and overlapping time alone no longer constitute a conflict. Existing authorization, editable-plan/revision guards on cruise-scoped routes, ordered times, cruise/port/tender bounds, allocation/capacity, currency, supplier, venue and lifecycle checks remain. Activity scheduleConflicts checks existence then returns zero for the removed exclusive-venue rule. Operations completeness still requires a dedicated area and planned shore destination. This addendum supersedes obsolete exclusive-venue branches in the historical static trace above; unrelated branches keep their previous review status.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAuthority('ACTIVITY_UPDATE')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ActivityController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PUT /activities/{id}
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: update request
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
