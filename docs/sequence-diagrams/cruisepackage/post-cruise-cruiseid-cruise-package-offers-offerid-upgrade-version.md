# POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/upgrade-version

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/upgrade-version — DepartureCruisePackageController.upgrade
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DepartureCruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/cruise-package-offers/{offerId}/upgrade-version + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke upgrade(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.upgradeOffer()

    Note over Controller,Service: Source branch: for var binding:bindings
    Note over Controller,Service: Source branch: if !"PUBLISHED".equals(version.getStatus())
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Cabin\" WHERE ship_id=? AND cabin_type::text=?
    Note over Controller,Service: Source branch: if !cabinMappings.requireCategory(input.logicalCategoryId(),version.getId()).name().equals(input.logicalCategory())
    Note over Controller,Service: Source branch: if !input.salesStart().isBefore(input.salesEnd())
    Note over Controller,Service: Source branch: if !offer.getCruiseId().equals(cruiseId)) fail("FARE_OFFER_CRUISE_MISMATCH","Gói vé không thuộc chuyến khởi hành."
    Note over Controller,Service: Source branch: if !old.getTemplateId().equals(version.getTemplateId())) fail("FARE_TEMPLATE_MISMATCH","Chỉ nâng phiên bản trong cùng mẫu gói vé."
    Note over Controller,Service: Source branch: if !version.getLogicalCategories().contains(input.logicalCategory())
    Note over Controller,Service: Source branch: if binding.getOnboardActivityId()!=null) names.put(binding.getId(
    Note over Controller,Service: Source branch: if binding.getShoreExcursionId()!=null) names.put(binding.getId(
    Note over Controller,Service: Source branch: if expected==null||actual!=expected
    Note over Controller,Service: Source branch: if offer.getTemplateVersionId().equals(input.offer().templateVersionId())
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
