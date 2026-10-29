# PATCH /tour-groups/{id}/cancel

```mermaid
sequenceDiagram
    title PATCH /tour-groups/{id}/cancel — TourGroupController.cancel
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TourGroupController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /tour-groups/{id}/cancel + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke cancel(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: tourGroupService.cancel()

    Note over Controller,Service: Source branch: for TourGroupPassenger assignment : assignments
    Note over Controller,Service: Source branch: if assignment.getAssignmentStatus(
    Note over Controller,Service: Source branch: if group.getStatus(
    Note over Controller,Service: Source branch: if reason == null || reason.isBlank()
        Note over Service,Repository: No @Transactional found on traced service method; repository calls may still join an outer transaction. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourGroupPassengerRepository.saveAll()
            Service->>Repository: TourGroupRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
