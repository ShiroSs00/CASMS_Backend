# POST /trip-schedule-versions/{versionId}/finalize

```mermaid
sequenceDiagram
    title POST /trip-schedule-versions/{versionId}/finalize — TripScheduleVersionController.finalizeTemplate
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripScheduleVersionController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /trip-schedule-versions/{versionId}/finalize + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke finalizeTemplate(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: versionService.finalizeVersion()

    Note over Controller,Service: Source branch: for TripPlanDay day : days
    Note over Controller,Service: Source branch: for TripPlanDay day : version.getDays()
    Note over Controller,Service: Source branch: for TripPlanEntry entry : entries
    Note over Controller,Service: Source branch: for TripPlanEntry entry : version.getEntries()
    Note over Controller,Service: Source branch: for TripPlanStop stop : stops
    Note over Controller,Service: Source branch: for TripPlanStop stop : version.getStops()
    Note over Controller,Service: Source branch: for byte b : hash
    Note over Controller,Service: Source branch: for int d = stop.getArrivalDayNumber(); d <= stop.getDepartureDayNumber(); d++
    Note over Controller,Service: Source branch: for int i = 0; i < stops.size(); i++
    Note over Controller,Service: Source branch: for int i = 1; i <= trip.getDurationDays(); i++
    Note over Controller,Service: Source branch: for int j = i + 1; j < stops.size(); j++
    Note over Controller,Service: Source branch: if !errorViolations.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TripRepository.flush()
            Service->>Repository: TripRepository.save()
            Service->>Repository: TripScheduleVersionRepository.findById()
            Service->>Repository: TripScheduleVersionRepository.flush()
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
