# POST /itineraries/{itineraryId}/publish

```mermaid
sequenceDiagram
    title POST /itineraries/{itineraryId}/publish — ItineraryController.publish
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ItineraryController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /itineraries/{itineraryId}/publish + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke publish(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.publish()

    Note over Controller,Service: Source branch: for int i = 0; i < days.size(); i++
    Note over Controller,Service: Source branch: for int i = 1; i < days.size(); i++
    Note over Controller,Service: Source branch: if !days.get(i).getDate().equals(days.get(i-1).getDate().plusDays(1))
    Note over Controller,Service: Source branch: if days.get(i).getDayNumber() != i + 1
    Note over Controller,Service: Source branch: if itinerary.getDays() != null
    Note over Controller,Service: Source branch: if itinerary.getDays() == null || itinerary.getDays().isEmpty()
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.APPROVED
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ItineraryRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
