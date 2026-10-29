# POST /cruises/{cruiseId}/pricing-policy/submit

```mermaid
sequenceDiagram
    title POST /cruises/{cruiseId}/pricing-policy/submit — CruisePricingPolicyController.submit
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePricingPolicyController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /cruises/{cruiseId}/pricing-policy/submit + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke submit(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: pricingPolicyService.review()

    Note over Controller,Service: Source branch: for CabinType type : CabinType.values()
    Note over Controller,Service: Source branch: if !"USD".equals(p.getCurrency())
    Note over Controller,Service: Source branch: if !Objects.equals(p.getSubmittedSnapshot(),snapshot(p))||!Objects.equals(p.getSnapshotHash(),hash(snapshot(p)))
    Note over Controller,Service: Source branch: if Objects.equals(p.getSubmittedBy(),actorId
    Note over Controller,Service: Source branch: if action.equals("approve")
    Note over Controller,Service: Source branch: if action.equals("return")
    Note over Controller,Service: Source branch: if auth==null || !(auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal
    Note over Controller,Service: Source branch: if count > 0 && policy.getBaseFareForCabinType(type) == null
    Note over Controller,Service: Source branch: if cruise.getShip() != null
    Note over Controller,Service: Source branch: if expected==null || expected!=(policy==null?0:policy.getRevision()))throw conflict("STALE_REVISION"
    Note over Controller,Service: Source branch: if p.getStatus()!=PricingPolicyStatus.DRAFT)throw conflict("INVALID_PRICING_TRANSITION"
    Note over Controller,Service: Source branch: if p.getStatus()!=PricingPolicyStatus.PENDING_APPROVAL)throw conflict("INVALID_PRICING_TRANSITION"
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: CruisePricingPolicyRepository.findByCruise_CruiseId()
            Service->>Repository: CruiseRepository.findByIdForUpdate()
            Service->>Repository: UserRepository.findById()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws BusinessException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws org.springframework.security.access.AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
