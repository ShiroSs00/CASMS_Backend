# POST /trips/{tripId}/versions/{versionNumber}/return-to-draft

```mermaid
sequenceDiagram
    title POST /trips/{tripId}/versions/{versionNumber}/return-to-draft — LegacyTripScheduleVersionController.returnToDraft
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as LegacyTripScheduleVersionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trips/{tripId}/versions/{versionNumber}/return-to-draft + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke returnToDraft(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: versionService.returnToDraft()

    Note over Controller,Service: Source branch: if auditService != null
    Note over Controller,Service: Source branch: if request.getExpectedRevision() != null && !request.getExpectedRevision().equals(version.getRevision())
    Note over Controller,Service: Source branch: if request.getRejectionReason() == null || request.getRejectionReason().isBlank()
    Note over Controller,Service: Source branch: if version.getStatus() != TripScheduleVersionStatus.IN_REVIEW
    Note over Controller,Service: Source branch: if version.getTrip().getStatus() == TripStatus.ARCHIVED
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
