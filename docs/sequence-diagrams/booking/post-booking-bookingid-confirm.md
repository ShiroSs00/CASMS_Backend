# POST /booking/{bookingId}/confirm

```mermaid
sequenceDiagram
    title POST /booking/{bookingId}/confirm — BookingController.confirm
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as BookingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /booking/{bookingId}/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirm(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: bookingService.confirm()

    Note over Controller,Service: Source branch: for BookingPassenger passenger : capacityPassengers
    Note over Controller,Service: Source branch: for UUID cabinId : cabinIds
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Cancelled
    Note over Controller,Service: Source branch: if booking.getBookingStatus() == BookingStatus.Confirmed
    Note over Controller,Service: Source branch: if capacityLockedCruise.getShip(
    Note over Controller,Service: Source branch: if capacityLockedCruise.getStatus() == CruiseStatus.CANCELLED
    Note over Controller,Service: Source branch: if capacityLockedCruise.getStoppedAt() != null
    Note over Controller,Service: Source branch: if collectedAmount.compareTo(booking.getRequiredDepositAmount()) < 0
    Note over Controller,Service: Source branch: if contract.getStatus() != BookingContractStatus.Accepted
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.OPEN_FOR_SALE
    Note over Controller,Service: Source branch: if itinerary.getStatus() == ItineraryStatus.SUSPENDED
    Note over Controller,Service: Source branch: if passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
