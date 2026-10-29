# POST /cruise-stops/{cruiseStopId}/planned-departure

```mermaid
sequenceDiagram
    title POST /cruise-stops/{cruiseStopId}/planned-departure — CruiseStopController.setPlannedDeparture
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStopController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-stops/{cruiseStopId}/planned-departure + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke setPlannedDeparture(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.setPlannedDeparture()

    Note over Controller,Service: Source branch: for CruiseStop other : existingStops
    Note over Controller,Service: Source branch: for ShoreExcursion exc : excursions
    Note over Controller,Service: Source branch: if !isSamePort
    Note over Controller,Service: Source branch: if !plannedDeparture.isAfter(stop.getPlannedArrival())
    Note over Controller,Service: Source branch: if Boolean.TRUE.equals(itinerary.getApprovalInherited()) || !Boolean.TRUE.equals(itinerary.getContentModifiedAfterMaterialization())
    Note over Controller,Service: Source branch: if arrival == null || departure == null
    Note over Controller,Service: Source branch: if arrival.equals(otherDep) || otherArr.equals(departure)
    Note over Controller,Service: Source branch: if arrival.isBefore(otherDep) && otherArr.isBefore(departure)
    Note over Controller,Service: Source branch: if currentStopId != null && currentStopId.equals(other.getCruiseStopId())
    Note over Controller,Service: Source branch: if exc.getPlannedReturnTime() != null && effectiveDeadline != null && exc.getPlannedReturnTime().isAfter(effectiveDeadline)
    Note over Controller,Service: Source branch: if exc.getStartTime() != null && arrival != null && exc.getStartTime().isBefore(arrival)
    Note over Controller,Service: Source branch: if itinerary != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc()
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
