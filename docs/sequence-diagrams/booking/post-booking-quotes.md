# POST /booking/quotes

```mermaid
sequenceDiagram
    title POST /booking/quotes — BookingController.createQuote
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /booking/quotes + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createQuote(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.createQuote()

    Note over Controller,Service: Source branch: for Map.Entry<CabinType, Long> entry : requestedPassengersByType.entrySet()
    Note over Controller,Service: Source branch: for Map.Entry<UUID, List<PassengerQuoteItem>> entry : cabinPassengersMap.entrySet()
    Note over Controller,Service: Source branch: for PassengerQuoteItem p : request.getPassengers()
    Note over Controller,Service: Source branch: for PassengerQuoteItem pReq : request.getPassengers()
    Note over Controller,Service: Source branch: for byte b : hash
    Note over Controller,Service: Source branch: if !assigned
    Note over Controller,Service: Source branch: if !cabin.getShip().getShipId().equals(cruise.getShip().getShipId())
    Note over Controller,Service: Source branch: if !request.getCurrency().trim().equalsIgnoreCase(policy.getCurrency())
    Note over Controller,Service: Source branch: if "CABIN_ONLY".equals(saleScope)
    Note over Controller,Service: Source branch: if baseFare == null
    Note over Controller,Service: Source branch: if cabin.getStatus() != CabinStatus.AVAILABLE
    Note over Controller,Service: Source branch: if cruise.getDepartureDatetime() != null && cruise.getDepartureDatetime().isBefore(Instant.now())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.countActivePassengersByCruiseId()
            Service->>Repository: BookingQuoteRepository.save()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise()
            Service->>Repository: CabinRepository.findById()
            Service->>Repository: CabinRepository.sumCapacityByShipIdAndCabinType()
            Service->>Repository: CruisePricingPolicyRepository.findByCruise_CruiseId()
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseId()
            Service->>Repository: TourPackageRepository.findById()
            Service->>Repository: UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
