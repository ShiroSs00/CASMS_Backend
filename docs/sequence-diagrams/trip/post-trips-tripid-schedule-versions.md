# POST /trips/{tripId}/schedule-versions

```mermaid
sequenceDiagram
    title POST /trips/{tripId}/schedule-versions — TripController.createScheduleVersion
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trips/{tripId}/schedule-versions + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createScheduleVersion(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: tripService.createScheduleVersion()

    Note over Controller,Service: Source branch: for TripPlanDay oldDay : sourceVer.getDays()
    Note over Controller,Service: Source branch: for TripPlanEntry oldEntry : sourceVer.getEntries()
    Note over Controller,Service: Source branch: for TripPlanStop oldStop : sourceVer.getStops()
    Note over Controller,Service: Source branch: for int i = 1; i <= trip.getDurationDays(); i++
    Note over Controller,Service: Source branch: if !sourceVer.getTrip().getTripId().equals(tripId)
    Note over Controller,Service: Source branch: if actorId != null
    Note over Controller,Service: Source branch: if e.getCabin() != null
    Note over Controller,Service: Source branch: if e.getCategory() != null
    Note over Controller,Service: Source branch: if e.getDay() != null
    Note over Controller,Service: Source branch: if e.getDeck() != null
    Note over Controller,Service: Source branch: if e.getDestination() != null
    Note over Controller,Service: Source branch: if e.getLocationText() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripScheduleVersionRepository.existsByTrip_TripIdAndStatusIn()
            Service->>Repository: TripScheduleVersionRepository.findById()
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
