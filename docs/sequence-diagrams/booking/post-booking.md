# POST /booking

```mermaid
sequenceDiagram
    title POST /booking — BookingController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /booking + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.createBooking()
        Controller->>Service: idempotencyService.execute()

    Note over Controller,Service: Source branch: for Booking b : expired
    Note over Controller,Service: Source branch: for BookingPassenger bp : bookingPassengers
    Note over Controller,Service: Source branch: for BookingPassenger bp : passengers
    Note over Controller,Service: Source branch: for Installment inst : installments
    Note over Controller,Service: Source branch: for Map.Entry<CabinType, Long> entry : requestedPassengersByType.entrySet()
    Note over Controller,Service: Source branch: for PassengerBookingItemRequest pReq : request.getPassengers()
    Note over Controller,Service: Source branch: for UUID cabinId : sortedCabinIds
    Note over Controller,Service: Source branch: if !assignedToCruise
    Note over Controller,Service: Source branch: if !cabin.getShip().getShipId().equals(cruise.getShip().getShipId())
    Note over Controller,Service: Source branch: if !documentNumbers.add(doc)
    Note over Controller,Service: Source branch: if !isOwner && !isStaff
    Note over Controller,Service: Source branch: if !isStaff && (idempotencyKey == null || idempotencyKey.isBlank())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingContractRepository.save()
            Service->>Repository: BookingPassengerPackageRepository.save()
            Service->>Repository: BookingPassengerRepository.countActivePassengersByCruiseId()
            Service->>Repository: BookingPassengerRepository.save()
            Service->>Repository: BookingQuoteRepository.findById()
            Service->>Repository: BookingQuoteRepository.save()
            Service->>Repository: BookingRepository.save()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinAndCruise()
            Service->>Repository: CabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise()
            Service->>Repository: CabinAssignmentRepository.save()
            Service->>Repository: CabinRepository.findByIdForUpdate()
            Service->>Repository: CabinRepository.sumCapacityByShipIdAndCabinType()
            Service->>Repository: CruisePricingPolicyRepository.findByCruise_CruiseId()
            Service->>Repository: CruiseRepository.findByIdForUpdate()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseId()
            Service->>Repository: PassengerRepository.findByUser_UserId()
            Service->>Repository: PassengerRepository.save()
            Service->>Repository: TourPackageRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
