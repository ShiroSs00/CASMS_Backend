# POST /cruise-stops/{cruiseStopId}/all-aboard-time

```mermaid
sequenceDiagram
    title POST /cruise-stops/{cruiseStopId}/all-aboard-time — CruiseStopController.setAllAboardTime
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStopController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-stops/{cruiseStopId}/all-aboard-time + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke setAllAboardTime(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.setAllAboardTime()

    Note over Controller,Service: Source branch: for ShoreExcursion exc : excursions
    Note over Controller,Service: Source branch: if allAboardTime != null
    Note over Controller,Service: Source branch: if exc.getPlannedReturnTime() != null && effectiveDeadline != null && exc.getPlannedReturnTime().isAfter(effectiveDeadline)
    Note over Controller,Service: Source branch: if exc.getStartTime() != null && arrival != null && exc.getStartTime().isBefore(arrival)
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.DRAFT
    Note over Controller,Service: Source branch: if shoreExcursionRepository == null || stop == null || stop.getCruiseStopId() == null
    Note over Controller,Service: Source branch: if stop.getArrivalItineraryDay() != null
    Note over Controller,Service: Source branch: if stop.getDestination() != null
    Note over Controller,Service: Source branch: if stop.getPlannedArrival() != null && allAboardTime.isBefore(stop.getPlannedArrival())
    Note over Controller,Service: Source branch: if stop.getPlannedDeparture() != null && allAboardTime.isAfter(stop.getPlannedDeparture())
    Note over Controller,Service: Source branch: if stop.getPort() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseStopRepository.save()
            Service->>Repository: ItineraryRepository.findById()
            Service->>Repository: ItineraryRepository.findByIdForUpdate()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
