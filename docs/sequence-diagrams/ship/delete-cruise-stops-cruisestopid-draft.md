# DELETE /cruise-stops/{cruiseStopId}/draft

```mermaid
sequenceDiagram
    title DELETE /cruise-stops/{cruiseStopId}/draft — CruiseStopController.removeDraft
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseStopController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /cruise-stops/{cruiseStopId}/draft + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke removeDraft(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.removeDraft()

    Note over Controller,Service: Source branch: if Boolean.TRUE.equals(itinerary.getApprovalInherited()) || !Boolean.TRUE.equals(itinerary.getContentModifiedAfterMaterialization())
    Note over Controller,Service: Source branch: if itinerary != null
    Note over Controller,Service: Source branch: if itinerary.getStatus() != ItineraryStatus.DRAFT
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseStopRepository.delete()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
