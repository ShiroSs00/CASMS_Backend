# PUT /servicepackage/services/{id}

```mermaid
sequenceDiagram
    title PUT /servicepackage/services/{id} — ServicepackageController.updateService
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /servicepackage/services/{id} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke updateService(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.saveService()
        Controller->>Service: service.service()

    Note over Controller,Service: Source branch: if s.getCapacity()!=null&&s.getCapacity()<1) throw new IllegalArgumentException("Capacity must be positive"
    Note over Controller,Service: Source branch: if s.getCurrencyCode()==null||s.getCurrencyCode().isBlank()) s.setCurrencyCode("USD"
    Note over Controller,Service: Source branch: if s.getPrice().signum()<0) throw new IllegalArgumentException("Non-negative service price requires currency"
    Note over Controller,Service: Source branch: if s.getPrice()==null) s.setPrice(BigDecimal.ZERO
    Note over Controller,Service: Source branch: if s.getServiceId()==null) s.setServiceCode(businessCodeGenerator.next("SV")
    Note over Controller,Service: Source branch: if s.getServiceName()==null||s.getServiceName().isBlank()) throw new IllegalArgumentException("Service code and name are required"
    Note over Controller,Service: Source branch: if s.getStatus()==null) s.setStatus(ServiceStatus.ACTIVE); return serviceRepo.save(s
    Note over Controller,Service: Source branch: if serviceRepo.existsByServiceCodeAndServiceIdNot(s.getServiceCode(),s.getServiceId()==null?new UUID(0,0):s.getServiceId())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ServiceRepository.existsByServiceCodeAndServiceIdNot()
            Service->>Repository: ServiceRepository.findById()
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
