# POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/publish

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/publish — DepartureCruisePackageController.publish
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DepartureCruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/publish + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke publish(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.publishOffer()

    Note over Controller,Service: Source branch: for var binding:bindings
    Note over Controller,Service: Source branch: if !"DRAFT".equals(offer.getStatus())) fail("FARE_OFFER_STATE_INVALID","Gói vé đã được phát hành."
    Note over Controller,Service: Source branch: if !assessment.eligible()
    Note over Controller,Service: Source branch: if !offer.getCruiseId().equals(cruiseId)) fail("FARE_OFFER_CRUISE_MISMATCH","Gói vé không thuộc chuyến khởi hành."
    Note over Controller,Service: Source branch: if binding.getOnboardActivityId()!=null) names.put(binding.getId(
    Note over Controller,Service: Source branch: if binding.getShoreExcursionId()!=null) names.put(binding.getId(
    Note over Controller,Service: Source branch: if expected==null||actual!=expected
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.flush()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
