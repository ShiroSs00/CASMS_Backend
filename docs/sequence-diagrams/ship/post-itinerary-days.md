# POST /itinerary-days

```mermaid
sequenceDiagram
    title POST /itinerary-days — ItineraryDayController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryDayController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itinerary-days + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if !PortTimeZoneHelper.isValidTimeZoneId(tz)
    Note over Controller,Service: Source branch: if day.getItinerary().getStatus() != ItineraryStatus.DRAFT
    Note over Controller,Service: Source branch: if day.getShipTimeZoneId() != null
    Note over Controller,Service: Source branch: if dayNumber == null
    Note over Controller,Service: Source branch: if itinerary.getCruise() != null && itinerary.getCruise().getDeparturePort() != null
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.DRAFT
    Note over Controller,Service: Source branch: if itineraryDayRepository.existsByItineraryItineraryIdAndDate(itinerary.getItineraryId(), date)
    Note over Controller,Service: Source branch: if itineraryDayRepository.existsByItineraryItineraryIdAndDayNumber(itinerary.getItineraryId(), dayNumber)
    Note over Controller,Service: Source branch: if tz != null && !tz.isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ItineraryDayRepository.existsByItineraryItineraryIdAndDate()
            Service->>Repository: ItineraryDayRepository.existsByItineraryItineraryIdAndDayNumber()
            Service->>Repository: ItineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc()
            Service->>Repository: ItineraryDayRepository.save()
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
