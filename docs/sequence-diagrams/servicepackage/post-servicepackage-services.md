# POST /servicepackage/services

```mermaid
sequenceDiagram
    title POST /servicepackage/services — ServicepackageController.createService
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/services + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createService(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.saveService()

    Note over Controller,Service: Source branch: if !s.isCanBuyPreCruise() && !s.isCanBuyOnboard()
    Note over Controller,Service: Source branch: if s.getCapacity() != null && s.getCapacity() < 1
    Note over Controller,Service: Source branch: if s.getCurrencyCode() == null || s.getCurrencyCode().isBlank()) s.setCurrencyCode("USD"
    Note over Controller,Service: Source branch: if s.getPreCruiseUsageLimit() != null) throw new IllegalArgumentException("UNLIMITED must not have a usage limit"
    Note over Controller,Service: Source branch: if s.getPreCruiseUsageLimit() == null || s.getPreCruiseUsageLimit() < 1
    Note over Controller,Service: Source branch: if s.getPreCruiseUsagePolicy() == ServiceUsagePolicy.PER_DAY && !s.isRequiresSchedule()
    Note over Controller,Service: Source branch: if s.getPreCruiseUsagePolicy() == ServiceUsagePolicy.UNLIMITED
    Note over Controller,Service: Source branch: if s.getPreCruiseUsagePolicy() == null
    Note over Controller,Service: Source branch: if s.getPrice() == null) s.setPrice(BigDecimal.ZERO
    Note over Controller,Service: Source branch: if s.getPrice().signum() < 0
    Note over Controller,Service: Source branch: if s.getServiceId() == null) s.setServiceCode(businessCodeGenerator.next("SV")
    Note over Controller,Service: Source branch: if s.getServiceName() == null || s.getServiceName().isBlank()
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ServiceRepository.existsByServiceCodeAndServiceIdNot()
            Service->>Repository: ServiceRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```

## Semantic review for service usage and activity catalog

This route was traced to its shared service callers for the V90 change. The reviewed end-to-end sequence, including security, controller, service/repository/database interactions, locks, transaction rollback, error branches and response changes, is in [the relevant flow](../SERVICE-ACTIVITY-FLOWS.md#service-catalog). API payloads and compatibility details are in [the client handoff](../../SERVICE-ACTIVITY-API-CHANGE.md). The source-level diagram above is a partial static trace, not a claim of complete semantic verification.

ServiceView returns purchase-stage flags, period policy/limit and time zone. POST/PUT validate stage and quota configuration; no occurrence date/time is stored. Generic availability remains a capacity check, not passenger quota availability.
