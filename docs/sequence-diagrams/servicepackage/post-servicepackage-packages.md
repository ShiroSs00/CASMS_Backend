# POST /servicepackage/packages

```mermaid
sequenceDiagram
    title POST /servicepackage/packages — ServicepackageController.createPackage
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/packages + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createPackage(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.savePackage()

    Note over Controller,Service: Source branch: if p.getCurrencyCode()==null||p.getCurrencyCode().isBlank()) p.setCurrencyCode("USD"
    Note over Controller,Service: Source branch: if p.getPackageName()==null||p.getPackageName().isBlank())throw new IllegalArgumentException("Package code and name are required"
    Note over Controller,Service: Source branch: if p.getPrice()==null||p.getPrice().signum()<0)throw new IllegalArgumentException("Package requires non-negative price and currency"
    Note over Controller,Service: Source branch: if p.getStartDate()!=null&&p.getEndDate()!=null&&p.getEndDate().isBefore(p.getStartDate())
    Note over Controller,Service: Source branch: if p.getStatus()==null)p.setStatus(TourPackageStatus.DRAFT); else if(p.getTourPackageId()==null&&p.getStatus(
    Note over Controller,Service: Source branch: if p.getTourPackageId()==null) p.setPackageCode(businessCodeGenerator.next("PK")
    Note over Controller,Service: Source branch: if packageRepo.existsByPackageCodeAndTourPackageIdNot(p.getPackageCode(),p.getTourPackageId()==null?new UUID(0,0):p.getTourPackageId(
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourPackageRepository.existsByPackageCodeAndTourPackageIdNot()
            Service->>Repository: TourPackageRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
