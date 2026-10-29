# POST /trip-schedule-versions/{versionId}/return

```mermaid
sequenceDiagram
    title POST /trip-schedule-versions/{versionId}/return — TripScheduleVersionController.returnToDraft
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripScheduleVersionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trip-schedule-versions/{versionId}/return + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke returnToDraft(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: versionService.returnToDraftById()

    Note over Controller,Service: Source branch: if auditService != null
    Note over Controller,Service: Source branch: if request.getExpectedRevision() != null && !request.getExpectedRevision().equals(version.getRevision())
    Note over Controller,Service: Source branch: if request.getRejectionReason() == null || request.getRejectionReason().isBlank()
    Note over Controller,Service: Source branch: if version.getStatus() != TripScheduleVersionStatus.IN_REVIEW
    Note over Controller,Service: Source branch: if version.getTrip().getStatus() == TripStatus.ARCHIVED
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripScheduleVersionRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
