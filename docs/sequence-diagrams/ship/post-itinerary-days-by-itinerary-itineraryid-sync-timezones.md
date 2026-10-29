# POST /itinerary-days/by-itinerary/{itineraryId}/sync-timezones

```mermaid
sequenceDiagram
    title POST /itinerary-days/by-itinerary/{itineraryId}/sync-timezones — ItineraryDayController.syncShipTimeZones
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryDayController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itinerary-days/by-itinerary/{itineraryId}/sync-timezones + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke syncShipTimeZones(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.syncShipTimeZonesFromStops()

    Note over Controller,Service: Source branch: for ItineraryDay day : days
    Note over Controller,Service: Source branch: if day.getArrivalStops() != null && !day.getArrivalStops().isEmpty()
    Note over Controller,Service: Source branch: if day.getDepartureStops() != null && !day.getDepartureStops().isEmpty()
    Note over Controller,Service: Source branch: if day.getItinerary().getStatus() != ItineraryStatus.DRAFT
    Note over Controller,Service: Source branch: if day.getShipTimeZoneId() == null || !PortTimeZoneHelper.isValidTimeZoneId(day.getShipTimeZoneId())
    Note over Controller,Service: Source branch: if days.isEmpty()
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.DRAFT && itinerary.getStatus() != ItineraryStatus.PENDING_APPROVAL
    Note over Controller,Service: Source branch: if stop != null && stop.getPort() != null && stop.getPort().getTimeZoneId() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ItineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc()
            Service->>Repository: ItineraryDayRepository.saveAll()
            Service->>Repository: ItineraryRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
