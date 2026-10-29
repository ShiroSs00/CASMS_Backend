# POST /trips/{tripId}/versions/{versionId}/days/manage

```mermaid
sequenceDiagram
    title POST /trips/{tripId}/versions/{versionId}/days/manage — TripController.manageDays
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trips/{tripId}/versions/{versionId}/days/manage + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke manageDays(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: tripService.manageDays()

    Note over Controller,Service: Source branch: for TripPlanDay d : draftVer.getDays()
    Note over Controller,Service: Source branch: for TripPlanEntry e : draftVer.getEntries()
    Note over Controller,Service: Source branch: for TripPlanStop s : draftVer.getStops()
    Note over Controller,Service: Source branch: for TripScheduleVersion draftVer : draftVersions
    Note over Controller,Service: Source branch: if !conflictingEntries.isEmpty()
    Note over Controller,Service: Source branch: if !conflictingStops.isEmpty()
    Note over Controller,Service: Source branch: if !draftVer.getVersionId().equals(versionId)
    Note over Controller,Service: Source branch: if !version.getTrip().getTripId().equals(tripId)
    Note over Controller,Service: Source branch: if !versions.isEmpty()
    Note over Controller,Service: Source branch: if auditService != null
    Note over Controller,Service: Source branch: if d.getDayNumber() > targetDay
    Note over Controller,Service: Source branch: if d.getTitle() == null || d.getTitle().matches("^Ngày \\d+$")
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripRepository.flush()
            Service->>Repository: TripRepository.save()
            Service->>Repository: TripScheduleVersionRepository.existsByTrip_TripIdAndStatus()
            Service->>Repository: TripScheduleVersionRepository.findById()
            Service->>Repository: TripScheduleVersionRepository.findByTrip_TripIdOrderByVersionNumberAsc()
            Service->>Repository: TripScheduleVersionRepository.flush()
            Service->>Repository: TripScheduleVersionRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
