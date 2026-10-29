# POST /trips/{tripId}/versions/new-draft

```mermaid
sequenceDiagram
    title POST /trips/{tripId}/versions/new-draft — LegacyTripScheduleVersionController.createNextDraftVersion
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as LegacyTripScheduleVersionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trips/{tripId}/versions/new-draft + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createNextDraftVersion(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: versionService.createNextDraftVersion()

    Note over Controller,Service: Source branch: for TripPlanDay day : version.getDays()
    Note over Controller,Service: Source branch: for TripPlanDay oldDay : latestPublished.getDays()
    Note over Controller,Service: Source branch: for TripPlanEntry entry : version.getEntries()
    Note over Controller,Service: Source branch: for TripPlanEntry oldEntry : latestPublished.getEntries()
    Note over Controller,Service: Source branch: for TripPlanStop oldStop : latestPublished.getStops()
    Note over Controller,Service: Source branch: for TripPlanStop stop : version.getStops()
    Note over Controller,Service: Source branch: for byte b : hash
    Note over Controller,Service: Source branch: if !hasPublished
    Note over Controller,Service: Source branch: if actorId != null
    Note over Controller,Service: Source branch: if hasUnfinished
    Note over Controller,Service: Source branch: if hex.length() == 1) hexString.append('0'
    Note over Controller,Service: Source branch: if oldEntry.getStop() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripRepository.findById()
            Service->>Repository: TripScheduleVersionRepository.existsByTrip_TripIdAndStatusIn()
            Service->>Repository: TripScheduleVersionRepository.findByTrip_TripIdOrderByVersionNumberAsc()
            Service->>Repository: TripScheduleVersionRepository.save()
            Service->>Repository: UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
