# GET /cruise/{cruiseId}/schedule

```mermaid
sequenceDiagram
    title GET /cruise/{cruiseId}/schedule — CruiseController.getCruiseSchedule
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /cruise/{cruiseId}/schedule + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getCruiseSchedule(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: scheduleMaterializationService.getCruiseSchedule()

    Note over Controller,Service: Source branch: for CruiseStop stop : stops
    Note over Controller,Service: Source branch: for ItineraryDay d : days
    Note over Controller,Service: Source branch: for OnboardActivity act : activities
    Note over Controller,Service: Source branch: for ShoreExcursion exc : excursions
    Note over Controller,Service: Source branch: if Objects.equals(actDate, d.getDate())
    Note over Controller,Service: Source branch: if Objects.equals(excDate, d.getDate())
    Note over Controller,Service: Source branch: if act.getStartTime() != null
    Note over Controller,Service: Source branch: if dayId != null
    Note over Controller,Service: Source branch: if exc.getStartTime() != null
    Note over Controller,Service: Source branch: if itinerary == null
    Note over Controller,Service: Source branch: if s.getNotes() != null && s.getNotes().startsWith("sourceStopId:")
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: CruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc()
            Service->>Repository: ItineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseId()
            Service->>Repository: OnboardActivityRepository.findByCruise_CruiseId()
            Service->>Repository: ShoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
