# POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/prices/{priceId}/submit

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/prices/{priceId}/submit — DepartureCruisePackageController.submit
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DepartureCruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/prices/{priceId}/submit + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke submit(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.reviewPrice()

    Note over Controller,Service: Source branch: for var binding:bindings
    Note over Controller,Service: Source branch: if !"DRAFT".equals(price.getStatus())) fail("FARE_PRICE_STATE_INVALID","Chỉ gửi duyệt revision giá nháp."
    Note over Controller,Service: Source branch: if !"SUBMITTED".equals(price.getStatus())) fail("FARE_PRICE_STATE_INVALID","Chỉ xét duyệt revision giá đã gửi."
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(jdbc.queryForObject("SELECT status IN ('DRAFT','PUBLISHED'
    Note over Controller,Service: Source branch: if !List.of("approve","return").contains(action)) fail("FARE_REVIEW_ACTION_INVALID","Thao tác duyệt không hợp lệ."
    Note over Controller,Service: Source branch: if !offer.getCruiseId().equals(cruiseId)) fail("FARE_OFFER_CRUISE_MISMATCH","Gói vé không thuộc chuyến khởi hành."
    Note over Controller,Service: Source branch: if !price.getOfferId().equals(offerId)) fail("FARE_PRICE_OFFER_MISMATCH","Revision giá không thuộc gói vé của chuyến."
    Note over Controller,Service: Source branch: if !price.getTemplateVersionId().equals(offer.getTemplateVersionId())||price.getOfferCommercialRevision(
    Note over Controller,Service: Source branch: if "return".equals(action)&&(input.reason()==null||input.reason().isBlank())
    Note over Controller,Service: Source branch: if "submit".equals(action)
    Note over Controller,Service: Source branch: if actor.equals(price.getCreatedBy())||actor.equals(price.getSubmittedBy())
    Note over Controller,Service: Source branch: if binding.getOnboardActivityId()!=null) names.put(binding.getId(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.flush()
            Service->>Repository: CruisePackageRepository.increment()
            Service->>Repository: CruisePackageRepository.prices()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
