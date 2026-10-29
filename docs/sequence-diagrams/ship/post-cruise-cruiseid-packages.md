# POST /cruise/{cruiseId}/packages

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/packages — CruiseController.assignPackage
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/packages + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke assignPackage(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: packageService.assignPackage()

    Note over Controller,Service: Source branch: if cruiseTourPackageRepository.existsByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, tourPackage.getTourPackageId())
    Note over Controller,Service: Source branch: if tourPackage.getStatus() != TourPackageStatus.ACTIVE
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruiseRepository.findById()
            Service->>Repository: CruiseTourPackageRepository.existsByCruise_CruiseIdAndTourPackage_TourPackageId()
            Service->>Repository: CruiseTourPackageRepository.save()
            Service->>Repository: TourPackageRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
