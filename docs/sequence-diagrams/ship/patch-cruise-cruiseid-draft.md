# PATCH /cruise/{cruiseId}/draft

```mermaid
sequenceDiagram
    title PATCH /cruise/{cruiseId}/draft — CruiseController.updateDraft
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruiseController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PATCH /cruise/{cruiseId}/draft + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke updateDraft(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.updateDraft()

    Note over Controller,Service: Source branch: if !Objects.equals(oldDep, effectiveDeparture
    Note over Controller,Service: Source branch: if !Set.of("DOMESTIC", "INTERNATIONAL").contains(request.getPlannedScope())
    Note over Controller,Service: Source branch: if !Set.of("FIXED", "CUSTOM").contains(request.getItineraryMode())
    Note over Controller,Service: Source branch: if !days.isEmpty() && days.size() != expectedDays
    Note over Controller,Service: Source branch: if (cruise.getSourceVersion() != null || cruise.getSourceTrip() != null
    Note over Controller,Service: Source branch: if bookedCount > 0
    Note over Controller,Service: Source branch: if cruise == null || cruise.getDepartureDatetime() == null
    Note over Controller,Service: Source branch: if cruise.getCompletionActor() != null
    Note over Controller,Service: Source branch: if cruise.getDepartureActor() != null
    Note over Controller,Service: Source branch: if cruise.getDeparturePort() != null
    Note over Controller,Service: Source branch: if cruise.getOpenedForSaleBy() != null
    Note over Controller,Service: Source branch: if cruise.getReturnPort() != null
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: BookingRepository.countByCruiseCruiseId()
            Service->>Repository: CruiseRepository.existsOverlappingCruisesExcluding()
            Service->>Repository: CruiseRepository.flush()
            Service->>Repository: CruiseRepository.save()
            Service->>Repository: ItineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc()
            Service->>Repository: ItineraryRepository.findByCruiseCruiseId()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    end
```

See [CASMS-75 catalog and staffing flows](../CASMS75-CATALOG-STAFFING-FLOWS.md) for the shared integration behavior.
