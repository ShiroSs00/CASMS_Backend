# POST /itineraries

```mermaid
sequenceDiagram
    title POST /itineraries — ItineraryController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itineraries + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: if creatorUserId == null
    Note over Controller,Service: Source branch: if endDate.isBefore(startDate)
    Note over Controller,Service: Source branch: if itinerary.getDays() != null
    Note over Controller,Service: Source branch: if itineraryRepository.existsByCruiseCruiseId(request.getCruiseId())
    Note over Controller,Service: Source branch: if principal == null || principal.getUserId() == null
    Note over Controller,Service: Source branch: if type == null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: ItineraryRepository.existsByCruiseCruiseId()
            Service->>Repository: ItineraryRepository.save()
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
