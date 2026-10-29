# PATCH /cruise-stops/{cruiseStopId}

```mermaid
sequenceDiagram
    title PATCH /cruise-stops/{cruiseStopId} — CruiseStopController.update
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStopController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /cruise-stops/{cruiseStopId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke update(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.update()

    Note over Controller,Service: Source branch: for CruiseStop other : existingStops
    Note over Controller,Service: Source branch: for ShoreExcursion exc : excursions
    Note over Controller,Service: Source branch: if !isSamePort
    Note over Controller,Service: Source branch: if !plannedArrival.isBefore(stop.getPlannedDeparture())
    Note over Controller,Service: Source branch: if !plannedDeparture.isAfter(stop.getPlannedArrival())
    Note over Controller,Service: Source branch: if Boolean.TRUE.equals(itinerary.getApprovalInherited()) || !Boolean.TRUE.equals(itinerary.getContentModifiedAfterMaterialization())
    Note over Controller,Service: Source branch: if allAboardTime != null
    Note over Controller,Service: Source branch: if arrival == null || departure == null
    Note over Controller,Service: Source branch: if arrival.equals(otherDep) || otherArr.equals(departure)
    Note over Controller,Service: Source branch: if arrival.isBefore(otherDep) && otherArr.isBefore(departure)
    Note over Controller,Service: Source branch: if cruise.getSourceTrip() == null
    Note over Controller,Service: Source branch: if cruiseStopRepository.existsByItineraryItineraryIdAndStopOrder(stop.getItinerary().getItineraryId(), request.getStopOrder())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseStopRepository.existsByItineraryItineraryIdAndStopOrder()
            Service->>Repository: CruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc()
            Service->>Repository: CruiseStopRepository.save()
            Service->>Repository: DestinationRepository.findById()
            Service->>Repository: ItineraryDayRepository.findById()
            Service->>Repository: ItineraryRepository.findById()
            Service->>Repository: ItineraryRepository.findByIdForUpdate()
            Service->>Repository: PortRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
