# GET /trips/{tripId}/versions/{versionNumber}

```mermaid
sequenceDiagram
    title GET /trips/{tripId}/versions/{versionNumber} — LegacyTripScheduleVersionController.getVersionDetail
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as LegacyTripScheduleVersionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /trips/{tripId}/versions/{versionNumber} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getVersionDetail(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: versionService.getVersionDetail()

    Note over Controller,Service: Source branch: if version.getCreator() != null
    Note over Controller,Service: Source branch: if version.getPublishedBy() != null
    Note over Controller,Service: Source branch: if version.getSubmitter() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
