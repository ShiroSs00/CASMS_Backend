# PUT /trips/{tripId}

```mermaid
sequenceDiagram
    title PUT /trips/{tripId} — TripController.updateTrip
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as TripController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /trips/{tripId} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke updateTrip(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: tripService.updateTrip()

    Note over Controller,Service: Source branch: for TripPlanEntry entry : v.getEntries()
    Note over Controller,Service: Source branch: for TripPlanStop stop : v.getStops()
    Note over Controller,Service: Source branch: for TripScheduleVersion v : versions
    Note over Controller,Service: Source branch: if !"VN".equalsIgnoreCase(excCountry)
    Note over Controller,Service: Source branch: if !"VN".equalsIgnoreCase(portCountry)
    Note over Controller,Service: Source branch: if !invalidPortViolations.isEmpty()
    Note over Controller,Service: Source branch: if !request.getDurationGroup().isValidDays(trip.getDurationDays())
    Note over Controller,Service: Source branch: if !upperAllowed.contains(excCountry)
    Note over Controller,Service: Source branch: if !upperAllowed.contains(portCountry)
    Note over Controller,Service: Source branch: if "DOMESTIC".equalsIgnoreCase(targetScope)
    Note over Controller,Service: Source branch: if "INTERNATIONAL".equalsIgnoreCase(targetScope)
    Note over Controller,Service: Source branch: if (request.getDurationDays() != null && !request.getDurationDays().equals(trip.getDurationDays())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
