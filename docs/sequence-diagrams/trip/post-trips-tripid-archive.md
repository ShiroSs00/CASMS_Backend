# POST /trips/{tripId}/archive

```mermaid
sequenceDiagram
    title POST /trips/{tripId}/archive — TripController.archiveTrip
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trips/{tripId}/archive + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke archiveTrip(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: tripService.archiveTrip()

    Note over Controller,Service: Source branch: if !versions.isEmpty()
    Note over Controller,Service: Source branch: if auditService != null
    Note over Controller,Service: Source branch: if request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(trip.getRevision())
    Note over Controller,Service: Source branch: if trip.getCreatedByUser() != null
    Note over Controller,Service: Source branch: if trip.getStatus() == TripStatus.ARCHIVED
    Note over Controller,Service: Source branch: if versionRepository.existsByTrip_TripIdAndStatus(tripId, TripScheduleVersionStatus.IN_REVIEW)
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripRepository.flush()
            Service->>Repository: TripRepository.save()
            Service->>Repository: TripScheduleVersionRepository.existsByTrip_TripIdAndStatus()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
