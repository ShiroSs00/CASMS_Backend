# PATCH /shore-excursions/{id}/confirm

```mermaid
sequenceDiagram
    title PATCH /shore-excursions/{id}/confirm — ShoreExcursionController.confirm
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ShoreExcursionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /shore-excursions/{id}/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirm(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: shoreExcursionService.confirm()

    Note over Controller,Service: Source branch: if excursion.getStatus(
    Note over Controller,Service: Source branch: if excursion.getStatus() != expected
    Note over Controller,Service: Source branch: if excursion.getStatus() == ShoreExcursionStatus.FULL
    Note over Controller,Service: Source branch: if registered >= excursion.getMaximumCapacity()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ShoreExcursionRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```

## CASMS-75 behavior change

Same location/port-call and overlapping time alone no longer constitute a conflict. Existing authorization, editable-plan/revision guards on cruise-scoped routes, ordered times, cruise/port/tender bounds, allocation/capacity, currency, supplier, venue and lifecycle checks remain. Activity scheduleConflicts checks existence then returns zero for the removed exclusive-venue rule. Operations completeness still requires a dedicated area and planned shore destination. This addendum supersedes obsolete exclusive-venue branches in the historical static trace above; unrelated branches keep their previous review status.

Reviewed delta only; full shared flow and important branches: [ITINERARY-PROGRAM-FLOWS.md](../ITINERARY-PROGRAM-FLOWS.md). Permission evidence: `hasAuthority('EXCURSION_STATUS_CHANGE')` (also see class annotations and central review).

```mermaid
sequenceDiagram
    actor Client
    participant Security as Spring Security
    participant Controller as ShoreExcursionController
    participant Service as Application service / policy
    participant Repository as JDBC / JPA repository
    participant DB as PostgreSQL
    Client->>Security: PATCH /shore-excursions/{id}/confirm
    Security->>Controller: Existing authentication and effective authorization
    Controller->>Service: confirm request
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
