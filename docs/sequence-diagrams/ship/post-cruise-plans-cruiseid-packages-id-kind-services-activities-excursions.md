# POST /cruise-plans/{cruiseId}/packages/{id}/{kind:services|activities|excursions}

```mermaid
sequenceDiagram
    title POST /cruise-plans/{cruiseId}/packages/{id}/{kind:services|activities|excursions} — CruiseScopedProgramsController.component
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseScopedProgramsController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise-plans/{cruiseId}/packages/{id}/{kind:services|activities|excursions} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke component(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: packages.addPackageActivity()
        Controller->>Service: packages.addPackageExcursion()
        Controller->>Service: packages.addPackageService()
        Controller->>Service: packages.tourPackage()

    Note over Controller,Service: Source branch: if benefit == null) throw new IllegalArgumentException("Benefit type is required"
    Note over Controller,Service: Source branch: if p.getStatus(
    Note over Controller,Service: Source branch: if packageActivityRepo.existsById(key)
    Note over Controller,Service: Source branch: if packageExcursionRepo.existsById(key)
    Note over Controller,Service: Source branch: if packageServiceRepo.existsById(key)
    Note over Controller,Service: Source branch: switch kind
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: TourPackageActivityRepository.existsById()
            Service->>Repository: TourPackageActivityRepository.save()
            Service->>Repository: TourPackageExcursionRepository.existsById()
            Service->>Repository: TourPackageExcursionRepository.save()
            Service->>Repository: TourPackageRepository.findById()
            Service->>Repository: TourPackageServiceRepository.existsById()
            Service->>Repository: TourPackageServiceRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    end
```
