# POST /activities/{id}/actions/{action}

```mermaid
sequenceDiagram
    title POST /activities/{id}/actions/{action} — ActivityController.lifecycle
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ActivityController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /activities/{id}/actions/{action} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke lifecycle(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.lifecycle()

    Note over Controller,Service: Source branch: if a.getActivityCode(
    Note over Controller,Service: Source branch: if a.getCapacity() == null || a.getCapacity() < 1
    Note over Controller,Service: Source branch: if a.getCruise(
    Note over Controller,Service: Source branch: if a.getCruise() != null
    Note over Controller,Service: Source branch: if a.getPrice(
    Note over Controller,Service: Source branch: if a.getStartTime(
    Note over Controller,Service: Source branch: if a.getStartTime().isAfter(Instant.now())) invalid(
    Note over Controller,Service: Source branch: if a.getStatus(
    Note over Controller,Service: Source branch: if a.getStatus() != OnboardActivityStatus.IN_PROGRESS) invalid(
    Note over Controller,Service: Source branch: if a.getStatus() != OnboardActivityStatus.PLANNED) invalid(
    Note over Controller,Service: Source branch: if a.isCatalogEntry()) throw new IllegalArgumentException("Schedule the catalog activity on a cruise before lifecycle actions"
    Note over Controller,Service: Source branch: switch action
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws UnsupportedOperationException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws com.casms.common.exception.BusinessException; HTTP mapping requires exception-handler review
    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#activity-catalog-itinerary-and-occurrence). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

Catalog and timed occurrences share storage but have explicit catalogEntry identity. Catalog POST rejects schedule fields. Scheduling copies metadata and retains sourceActivityId. Operational activity reads return catalogEntry/sourceActivityId/category/responsibleUserId. Catalog lifecycle/registration is rejected; source deletion is protected. Cruise-scoped reads/writes require the existing cruise access and revision guards.
