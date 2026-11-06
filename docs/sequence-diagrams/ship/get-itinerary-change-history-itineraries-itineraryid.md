# GET /itinerary-change-history/itineraries/{itineraryId}

```mermaid
sequenceDiagram
    title GET /itinerary-change-history/itineraries/{itineraryId} — ItineraryChangeHistoryController.getChangeHistory
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryChangeHistoryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /itinerary-change-history/itineraries/{itineraryId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getChangeHistory(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.getChangeHistory()

    Note over Controller,Service: Source branch: if !itineraryRepository.existsById(itineraryId)
    Note over Controller,Service: Source branch: if itineraryId == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws EntityNotFoundException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
