# POST /itinerary-change-history

```mermaid
sequenceDiagram
    title POST /itinerary-change-history — ItineraryChangeHistoryController.append
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryChangeHistoryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itinerary-change-history + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke append(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.recordItineraryChange()

    Note over Controller,Service: Source branch: if !cruiseStop.getItinerary().getItineraryId(
    Note over Controller,Service: Source branch: if changedByUserId == null
    Note over Controller,Service: Source branch: if principal == null || principal.getUserId() == null
    Note over Controller,Service: Source branch: if request.cruiseStopId() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
