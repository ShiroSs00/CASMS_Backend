# POST /itineraries/{itineraryId}/validate-feasibility

```mermaid
sequenceDiagram
    title POST /itineraries/{itineraryId}/validate-feasibility — ItineraryController.validateFeasibility
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itineraries/{itineraryId}/validate-feasibility + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke validateFeasibility(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.validateFeasibility()

    Note over Controller,Service: Source branch: for int i = 0; i < days.size(); i++
    Note over Controller,Service: Source branch: if cruise.getDepartureDatetime() != null && cruise.getReturnDatetime() != null
    Note over Controller,Service: Source branch: if days == null || days.isEmpty()
    Note over Controller,Service: Source branch: if days.get(i).getDate().isBefore(itinerary.getStartDate()
    Note over Controller,Service: Source branch: if days.get(i).getDayNumber() != i + 1
    Note over Controller,Service: Source branch: if days.size() != expectedDayCount
    Note over Controller,Service: Source branch: if itinerary.getCruise() != null
    Note over Controller,Service: Source branch: if itinerary.getEndDate().isBefore(itinerary.getStartDate())
    Note over Controller,Service: Source branch: if itinerary.getStartDate() == null || itinerary.getEndDate() == null
    Note over Controller,Service: Source branch: if itinerary.getStartDate().isBefore(departureDate
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
