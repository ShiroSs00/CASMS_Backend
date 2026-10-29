# GET /itineraries/mine

```mermaid
sequenceDiagram
    title GET /itineraries/mine — ItineraryController.mine
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: GET /itineraries/mine + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke mine(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: passengerItineraryService.mine()

    Note over Controller,Service: Source branch: for CruiseStop stop : stops
    Note over Controller,Service: Source branch: if !itineraryIds.isEmpty()
    Note over Controller,Service: Source branch: if authenticatedUserId == null
    Note over Controller,Service: Source branch: if cruiseId == null
    Note over Controller,Service: Source branch: if passengers.isEmpty()) return List.of(
    Note over Controller,Service: Source branch: if principal == null || principal.getUserId() == null
    Note over Controller,Service: Source branch: if registration.getOnboardActivity() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingPassengerRepository.findPassengerTrips()
            Service->>Repository: ItineraryDayRepository.findByItineraryItineraryIdInOrderByItineraryItineraryIdAscDayNumberAsc()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseIdInAndStatusIn()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws org.springframework.security.access.AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
