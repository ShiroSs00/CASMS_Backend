# GET /booking/3d-available-cabins

```mermaid
sequenceDiagram
    title GET /booking/3d-available-cabins — BookingController.getThreeDimensionalAvailableCabins
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /booking/3d-available-cabins + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke getThreeDimensionalAvailableCabins(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.getThreeDimensionalAvailableCabins()

    Note over Controller,Service: Source branch: for Cabin cabin : cabins
    Note over Controller,Service: Source branch: if cabin.getModelWidth() != null || cabin.getModelHeight() != null || cabin.getModelDepth() != null
    Note over Controller,Service: Source branch: if cruise.getDepartureDatetime() != null && cruise.getDepartureDatetime().isBefore(Instant.now())
    Note over Controller,Service: Source branch: if cruise.getOpenedForSaleAt() == null || cruise.getStatus() != CruiseStatus.PUBLISHED
    Note over Controller,Service: Source branch: if cruise.getShip() == null
    Note over Controller,Service: Source branch: if cruise.getStoppedAt() != null
    Note over Controller,Service: Source branch: if includeModelPlacement
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE
    Note over Controller,Service: Source branch: if itinerary.getStatus() == ItineraryStatus.SUSPENDED
    Note over Controller,Service: Source branch: if mapping != null
    Note over Controller,Service: Source branch: if model != null) res.setModelId(model.getShipModelId()
    Note over Controller,Service: Source branch: if policyOpt.isEmpty()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
