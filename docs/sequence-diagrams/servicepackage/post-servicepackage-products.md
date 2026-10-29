# POST /servicepackage/products

```mermaid
sequenceDiagram
    title POST /servicepackage/products — ServicepackageController.createProduct
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as ServicepackageController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /servicepackage/products + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke createProduct(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.saveProduct()

    Note over Controller,Service: Source branch: if p.getCurrencyCode()==null||p.getCurrencyCode().isBlank()) p.setCurrencyCode("USD"
    Note over Controller,Service: Source branch: if p.getProductId()==null) p.setProductCode(businessCodeGenerator.next("PR")
    Note over Controller,Service: Source branch: if p.getProductName()==null||p.getProductName().isBlank()) throw new IllegalArgumentException("Product code and name are required"
    Note over Controller,Service: Source branch: if p.getStatus()==null)p.setStatus(ProductStatus.ACTIVE); return productRepo.save(p
    Note over Controller,Service: Source branch: if p.getUnitPrice()==null||p.getUnitPrice().signum()<0
    Note over Controller,Service: Source branch: if productRepo.existsByProductCodeAndProductIdNot(p.getProductCode(),p.getProductId()==null?new UUID(0,0):p.getProductId())
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        opt Repository interactions found in traced handler/service code
            Service->>Repository: ProductRepository.existsByProductCodeAndProductIdNot()
            Service->>Repository: ProductRepository.save()
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalArgumentException; HTTP mapping requires exception-handler review
    end
```
