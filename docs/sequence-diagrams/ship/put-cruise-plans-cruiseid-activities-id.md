# PUT /cruise-plans/{cruiseId}/activities/{id}

```mermaid
sequenceDiagram
    title PUT /cruise-plans/{cruiseId}/activities/{id} — CruiseScopedProgramsController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /cruise-plans/{cruiseId}/activities/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: activities.activity()
        Controller->>Service: activities.updateActivity()


        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OnboardActivityRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#activity-catalog-itinerary-and-occurrence). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Catalog and timed occurrences share storage but have explicit catalogEntry identity. Catalog POST rejects schedule fields. Scheduling copies metadata and retains sourceActivityId. Operational activity reads return catalogEntry/sourceActivityId/category/responsibleUserId. Catalog lifecycle/registration is rejected; source deletion is protected. Cruise-scoped reads/writes require the existing cruise access and revision guards.

## CASMS-75 behavior change

Same location/port-call and overlapping time alone no longer constitute a conflict. Existing authorization, editable-plan/revision guards on cruise-scoped routes, ordered times, cruise/port/tender bounds, allocation/capacity, currency, supplier, venue and lifecycle checks remain. Activity scheduleConflicts checks existence then returns zero for the removed exclusive-venue rule. Operations completeness still requires a dedicated area and planned shore destination. This addendum supersedes obsolete exclusive-venue branches in the historical static trace above; unrelated branches keep their previous review status.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as CruiseScopedProgramsController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PUT /cruise-plans/{cruiseId}/activities/{id}
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

## Staff scheduling constraint

A schedule edit checks active staff duties in the database transaction. An overlapping shift is rejected with 409 STAFF_WORK_TIME_CONFLICT. See [work scheduling](../ITINERARY-PROGRAM-FLOWS.md#dịch-vụ-và-ca-hoạt-động).
