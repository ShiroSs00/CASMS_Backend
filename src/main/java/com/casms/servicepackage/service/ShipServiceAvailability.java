package com.casms.servicepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Physical availability comes only from manual deck assignments, never model metadata or a schedule label. */
@Component @RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ShipServiceAvailability {
    private final JdbcTemplate jdbc;

    public void requirePackage(UUID cruiseId, UUID packageId) {
        requireServices(cruiseId, jdbc.queryForList("WITH RECURSIVE chain AS (SELECT tour_package_id,parent_package_id FROM \"TourPackage\" WHERE tour_package_id=? UNION SELECT p.tour_package_id,p.parent_package_id FROM \"TourPackage\" p JOIN chain c ON p.tour_package_id=c.parent_package_id) SELECT DISTINCT service_id FROM \"TourPackageService\" JOIN chain USING(tour_package_id)", UUID.class, packageId));
    }

    public void requireService(UUID cruiseId, UUID serviceId) { requireServices(cruiseId, List.of(serviceId)); }

    public void requireServiceForPackage(UUID packageId, UUID serviceId) {
        // Both cruise-owned drafts and catalogue packages already attached to cruises must be checked.
        var cruises=jdbc.queryForList("WITH RECURSIVE affected AS (SELECT tour_package_id,owner_cruise_id FROM \"TourPackage\" WHERE tour_package_id=? UNION SELECT p.tour_package_id,p.owner_cruise_id FROM \"TourPackage\" p JOIN affected a ON p.parent_package_id=a.tour_package_id) SELECT owner_cruise_id FROM affected WHERE owner_cruise_id IS NOT NULL UNION SELECT c.cruise_id FROM \"CruiseTourPackage\" c JOIN affected a USING(tour_package_id)", UUID.class,packageId);
        if(!cruises.isEmpty()) {
            var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_CRUISE_MANAGER")))
                throw new org.springframework.security.access.AccessDeniedException("Only CRUISE_MANAGER can attach services to cruise packages");
        }
        cruises.stream().sorted().forEach(id -> requireService(id,serviceId));
    }

    private void requireServices(UUID cruiseId, List<UUID> serviceIds) {
        var rows=jdbc.queryForList("SELECT ship_id FROM \"Cruise\" WHERE cruise_id=? FOR UPDATE",cruiseId);
        if(rows.isEmpty())throw new ResourceNotFoundException("Cruise",cruiseId);
        UUID shipId=(UUID)rows.getFirst().get("ship_id");
        if(shipId==null)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"CRUISE_SHIP_REQUIRED","Chuyến đi chưa được gán tàu, không thể gắn dịch vụ hoặc gói dịch vụ.");
        List<Map<String,Object>> missing=new ArrayList<>();
        for(UUID id:new LinkedHashSet<>(serviceIds)) {
            boolean present=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"DeckServiceAssignment\" a JOIN \"Deck\" d USING(deck_id) JOIN \"Service\" s USING(service_id) WHERE d.ship_id=? AND a.service_id=? AND s.status='ACTIVE')",Boolean.class,shipId,id));
            if(!present) {
                var names=jdbc.queryForList("SELECT service_name FROM \"Service\" WHERE service_id=?",String.class,id);
                missing.add(Map.of("serviceId",id,"serviceName",names.isEmpty()?id.toString():names.getFirst()));
            }
        }
        if(!missing.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"SERVICE_NOT_AVAILABLE_ON_SHIP",
                "Không thể gắn: dịch vụ chưa có trên tàu hoặc chưa hoạt động: "+String.join(", ",missing.stream().map(m->m.get("serviceName").toString()).toList()),
                Map.of("cruiseId",cruiseId,"shipId",shipId,"missingServices",missing));
    }
}
