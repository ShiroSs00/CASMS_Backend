# PATCH /tour-group-passengers/{id}/move

```mermaid
sequenceDiagram
    title PATCH /tour-group-passengers/{id}/move — TourGroupPassengerController.move
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TourGroupPassengerController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /tour-group-passengers/{id}/move + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke move(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.move()

    Note over Controller,Service: Source branch: if !sourceGroup.getShoreExcursion().getShoreExcursionId(
    Note over Controller,Service: Source branch: if assignmentId == null
    Note over Controller,Service: Source branch: if current.getAssignmentStatus(
    Note over Controller,Service: Source branch: if groupId == null
    Note over Controller,Service: Source branch: if sourceGroup.getTourGroupId().equals(targetGroupId)
    Note over Controller,Service: Source branch: if targetGroupId == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourGroupPassengerRepository.save()
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
