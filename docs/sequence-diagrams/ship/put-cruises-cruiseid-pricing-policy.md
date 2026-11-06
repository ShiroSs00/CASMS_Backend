# PUT /cruises/{cruiseId}/pricing-policy

```mermaid
sequenceDiagram
    title PUT /cruises/{cruiseId}/pricing-policy — CruisePricingPolicyController.savePricingPolicy
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePricingPolicyController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: PUT /cruises/{cruiseId}/pricing-policy + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke savePricingPolicy(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: pricingPolicyService.savePricingPolicy()


        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)

    end
```
