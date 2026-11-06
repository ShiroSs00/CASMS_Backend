# POST /cruise/{cruiseId}/cruise-package-addons

```mermaid
sequenceDiagram
    title POST /cruise/{cruiseId}/cruise-package-addons — CruisePackageAddonController.create
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePackageAddonController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruise/{cruiseId}/cruise-package-addons + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke create(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.create()

    Note over Controller,Service: Source branch: for var w:windows
    Note over Controller,Service: Source branch: for var w:windows)if(!stages.add(w.stage())||!w.startsAt().isBefore(w.endsAt())||w.endsAt(
    Note over Controller,Service: Source branch: if !(cruise.get("booking_opens_at") instanceof java.sql.Timestamp open)||!(cruise.get("booking_closes_at"
    Note over Controller,Service: Source branch: if !stages.add(w.stage())||!w.startsAt().isBefore(w.endsAt())||w.endsAt().isAfter(w.operationalCutoff())||w.operationalCutoff(
    Note over Controller,Service: Source branch: if auth==null||!(auth.getPrincipal() instanceof CasmsPrincipal actor)||!actor.hasRole(Role.ACTIVITY_SERVICE_MANAGER
    Note over Controller,Service: Source branch: if row.get("cutoff_limit")==null
    Note over Controller,Service: Source branch: if rows.size()!=1
    Note over Controller,Service: Source branch: if shipId==null)throw error("CRUISE_SHIP_REQUIRED","Cần phân công tàu trước khi cấu hình Add-on."
    Note over Controller,Service: Source branch: if type==CruisePackageContracts.ResourceType.SERVICE&&(row.get("unit_code")==null||row.get("unit_code").toString().isBlank())
    Note over Controller,Service: Source branch: if w.stage()==Stage.BOOKING
    Note over Controller,Service: Source branch: if w.stage()==Stage.PRE_DEPARTURE&&w.endsAt().isAfter(departure)||w.stage()==Stage.ONBOARD&&(w.startsAt().isBefore(departure
    Note over Controller,Service: Source branch: switch input.resourceType()
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
    Note over Service,Client: Source throws org.springframework.security.access.AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
