# POST /cruise/{cruiseId}/cruise-package-offers

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/cruise-package-offers — DepartureCruisePackageController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as DepartureCruisePackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/cruise-package-offers + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.createOffer()

    Note over Controller,Service: Source branch: for var binding:bindings
    Note over Controller,Service: Source branch: if !"PUBLISHED".equals(version.getStatus())
    Note over Controller,Service: Source branch: if !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Cabin\" WHERE ship_id=? AND cabin_type::text=?
    Note over Controller,Service: Source branch: if !cabinMappings.requireCategory(input.logicalCategoryId(),version.getId()).name().equals(input.logicalCategory())
    Note over Controller,Service: Source branch: if !input.salesStart().isBefore(input.salesEnd())
    Note over Controller,Service: Source branch: if !version.getLogicalCategories().contains(input.logicalCategory())
    Note over Controller,Service: Source branch: if binding.getOnboardActivityId()!=null) names.put(binding.getId(
    Note over Controller,Service: Source branch: if binding.getShoreExcursionId()!=null) names.put(binding.getId(
    Note over Controller,Service: Source branch: if ship==null) fail("FARE_SHIP_REQUIRED","Chuyến khởi hành chưa được phân công tàu."
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePackageRepository.flush()
            Service->>Repository: CruisePackageRepository.insert()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
