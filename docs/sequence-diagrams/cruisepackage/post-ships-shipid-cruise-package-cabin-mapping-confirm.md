# POST /ships/{shipId}/cruise-package-cabin-mapping/confirm

```mermaid
sequenceDiagram
    title POST /ships/{shipId}/cruise-package-cabin-mapping/confirm — CruisePackageCabinMappingController.confirm
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as CruisePackageCabinMappingController
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: POST /ships/{shipId}/cruise-package-cabin-mapping/confirm + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke confirm(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
        Controller->>Service: service.confirm()

    Note over Controller,Service: Source branch: for UUID cabinId:selection.cabinIds()
    Note over Controller,Service: Source branch: for var selection:input.assignments()
    Note over Controller,Service: Source branch: for var selection:input.assignments())for(UUID cabinId:selection.cabinIds()
    Note over Controller,Service: Source branch: if !Objects.equals(input.expectedMappingId(),current==null?null:current.get("mapping_id")
    Note over Controller,Service: Source branch: if !Set.of("PUBLISHED","SUPERSEDED").contains(state)
    Note over Controller,Service: Source branch: if !seenCabins.add(cabinId)
    Note over Controller,Service: Source branch: if !seenCategories.add(selection.logicalCategoryId())
    Note over Controller,Service: Source branch: if !shipId.equals(cabin.get("deck_ship_id"))||Boolean.TRUE.equals(cabin.get("service_level")
    Note over Controller,Service: Source branch: if !ships.existsById(shipId))throw error("SHIP_NOT_FOUND","Không tìm thấy tàu cần đối chiếu mapping."
    Note over Controller,Service: Source branch: if actor==null||!actor.hasRole(Role.SHIP_MANAGER)||!actor.hasAuthority("SHIP_CABIN_CATEGORY_MAPPING_CONFIRM")
        Note over Service,Repository: @Transactional present on traced service method. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
        Note over Controller,Repository: No direct repository interaction found in traced method body
        Service-->>Controller: Domain result
        Controller-->>Client: HTTP response (verify DTO and status)
    Note over Service,Client: Source throws IllegalStateException; HTTP mapping requires exception-handler review
    Note over Service,Client: Source throws org.springframework.security.access.AccessDeniedException; HTTP mapping requires exception-handler review
    end
```
