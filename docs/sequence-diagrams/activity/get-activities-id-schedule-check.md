# GET /activities/{id}/schedule-check

```mermaid
sequenceDiagram
    title GET /activities/{id}/schedule-check — ActivityController.scheduleCheck
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /activities/{id}/schedule-check + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke scheduleCheck(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.scheduleConflicts()

    Note over Controller,Service: Source branch: if a.getCruise() == null || a.getLocation() == null || a.getLocation().isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: OnboardActivityRepository.countScheduleConflicts()
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

Activity spaces allow simultaneous activities. scheduleConflicts checks activity existence then returns zero; response keeps its existing shape. Complete time/area/details validation occurs at operations submission. Full reviewed delta: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Existing static baseline above is not newly approved in its entirety.

```mermaid
sequenceDiagram
    actor Client
    participant Controller
    participant Service
    participant Repository
    participant DB as PostgreSQL
    Client->>Controller: Authorized request using existing route contract
    Controller->>Service: Validate program without exclusive venue rejection
    Service->>Repository: Retain scope, allocation and program checks
    Repository->>DB: Existing bound queries
    Service-->>Client: Existing response; errors preserve current mapping
```
