# DELETE /cruise/{cruiseId}/draft

```mermaid
sequenceDiagram
    title DELETE /cruise/{cruiseId}/draft — CruiseController.deleteDraft
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: DELETE /cruise/{cruiseId}/draft + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke deleteDraft(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.deleteDraft()

    Note over Controller,Service: Source branch: if bookingRepository.existsByCruiseCruiseId(cruiseId)
    Note over Controller,Service: Source branch: if cruise.getStatus() != CruiseStatus.DRAFT
    Note over Controller,Service: Source branch: if cruiseImpactTaskRepository != null
    Note over Controller,Service: Source branch: if cruiseRegulationRepository != null
    Note over Controller,Service: Source branch: if cruiseStaffRepository != null
    Note over Controller,Service: Source branch: if cruiseTourPackageRepository != null
    Note over Controller,Service: Source branch: if onboardActivityRepository != null
    Note over Controller,Service: Source branch: if shoreExcursionRepository != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingRepository.existsByCruiseCruiseId()
            Service->>Repository: CruiseImpactTaskRepository.deleteByCruiseCruiseId()
            Service->>Repository: CruiseRegulationRepository.deleteByCruise_CruiseId()
            Service->>Repository: CruiseRepository.delete()
            Service->>Repository: CruiseStaffRepository.deleteByCruise_CruiseId()
            Service->>Repository: CruiseStopRepository.deleteByItineraryItineraryId()
            Service->>Repository: CruiseStopRepository.flush()
            Service->>Repository: CruiseTourPackageRepository.deleteByCruise_CruiseId()
            Service->>Repository: ItineraryRepository.delete()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseId()
            Service->>Repository: ItineraryRepository.flush()
            Service->>Repository: OnboardActivityRepository.deleteByCruise_CruiseId()
            Service->>Repository: ShoreExcursionRepository.deleteByCruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```
