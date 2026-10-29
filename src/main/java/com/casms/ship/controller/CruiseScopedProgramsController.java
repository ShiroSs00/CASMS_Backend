package com.casms.ship.controller;

import com.casms.activity.controller.ActivityController.ActivityRequest;
import com.casms.activity.controller.ActivityController.ActivityView;
import com.casms.activity.dto.request.ShoreExcursionRequest;
import com.casms.activity.dto.response.ShoreExcursionResponse;
import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.service.ActivityService;
import com.casms.activity.service.ShoreExcursionService;
import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.BenefitType;
import com.casms.servicepackage.enums.TourPackageType;
import com.casms.servicepackage.service.ServicepackageService;
import com.casms.ship.dto.request.CruisePricingPolicyRequest;
import com.casms.ship.service.CruiseMainflowService;
import com.casms.ship.service.CruisePricingPolicyService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.casms.security.principal.CasmsPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** CRM can edit its Cruise programs without acquiring global activity/catalog grants. */
@RestController
@RequestMapping("/cruise-plans/{cruiseId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
@Transactional
public class CruiseScopedProgramsController {
    private final CruiseMainflowService plans;
    private final ActivityService activities;
    private final ShoreExcursionService excursions;
    private final ServicepackageService packages;
    private final CruisePricingPolicyService pricing;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final com.casms.servicepackage.service.ServiceScheduleManagementService schedules;
    public record ServingInput(@NotNull UUID serviceId,UUID cruiseStopId,UUID supplierId,@NotNull java.time.Instant startsAt,
                               @NotNull java.time.Instant endsAt,@NotBlank String location,@NotNull @Positive Integer capacity) {}
    @GetMapping("/program-options")
    @Transactional(readOnly=true)
    public ApiResponse<Map<String,Object>> options(@PathVariable UUID cruiseId){
        plans.get(cruiseId);
        return ApiResponse.success(Map.of(
            "ship",jdbc.queryForMap("SELECT c.ship_id AS id,s.ship_name AS name FROM \"Cruise\" c LEFT JOIN \"Ship\" s USING(ship_id) WHERE c.cruise_id=?",cruiseId),
            "decks",jdbc.queryForList("SELECT d.deck_id AS id,d.deck_name AS name,d.deck_number AS number,d.ship_id AS \"shipId\" FROM \"Deck\" d JOIN \"Cruise\" c ON c.ship_id=d.ship_id WHERE c.cruise_id=? ORDER BY d.deck_number,d.deck_id",cruiseId),
            "services",jdbc.queryForList("SELECT service_id AS id,service_name AS name,requires_schedule AS scheduled FROM \"Service\" WHERE status='ACTIVE' ORDER BY service_name"),
            "suppliers",jdbc.queryForList("SELECT supplier_id AS id,supplier_name AS name FROM \"Supplier\" WHERE status='ACTIVE' AND verification_status='VERIFIED' ORDER BY supplier_name"),
            "stops",jdbc.queryForList("SELECT s.cruise_stop_id AS id,s.stop_order AS sequence,p.port_name AS name,p.time_zone_id AS \"timeZoneId\" FROM \"CruiseStop\" s JOIN \"Port\" p USING(port_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=? ORDER BY s.stop_order",cruiseId)));
    }
    @GetMapping("/serving-windows")
    @Transactional(readOnly=true)
    public ApiResponse<List<Map<String,Object>>> windows(@PathVariable UUID cruiseId){plans.get(cruiseId);return ApiResponse.success(jdbc.queryForList("SELECT s.service_schedule_id AS id,s.service_id AS \"serviceId\",v.service_name AS name,s.starts_at AS \"startsAt\",s.ends_at AS \"endsAt\",s.capacity,s.location FROM \"ServiceSchedule\" s JOIN \"Service\" v USING(service_id) WHERE cruise_id=? ORDER BY starts_at",cruiseId));}
    @PostMapping("/serving-windows")
    public ApiResponse<Map<String,Object>> createWindow(@PathVariable UUID cruiseId,@RequestHeader("If-Match") Long expected,@Valid @RequestBody ServingInput input){
        start(cruiseId,expected);if(input.cruiseStopId()!=null)stopScope(cruiseId,input.cruiseStopId());
        var c=jdbc.queryForMap("SELECT departure_datetime,return_datetime FROM \"Cruise\" WHERE cruise_id=?",cruiseId);
        java.time.Instant departure=((java.sql.Timestamp)c.get("departure_datetime")).toInstant(),end=((java.sql.Timestamp)c.get("return_datetime")).toInstant();
        if(input.startsAt().isBefore(departure)||input.endsAt().isAfter(end))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"SERVICE_OUTSIDE_CRUISE","Serving window must fit Cruise");
        var saved=schedules.create(input.serviceId(),cruiseId,input.cruiseStopId(),input.supplierId(),input.startsAt(),input.endsAt(),input.location(),input.capacity());
        finish(cruiseId);return ApiResponse.success(Map.of("id",saved.getServiceScheduleId(),"cruiseId",cruiseId));
    }
    public record PackageInput(@NotBlank String name,String description,@NotNull TourPackageType type,
                               @NotNull @DecimalMin("0") BigDecimal price,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency) {}
    public record Component(@NotNull UUID referenceId,@NotNull BenefitType benefitType) {}
    public record PackageView(UUID id,UUID cruiseId,String name,String status,BigDecimal price,String currency) {}
    private void start(UUID id,Long expected) {
        plans.assertEditable(id);
        Long revision=jdbc.queryForObject("SELECT revision FROM \"Cruise\" WHERE cruise_id=?",Long.class,id);
        if(expected==null||!Objects.equals(expected,revision))throw new BusinessException(HttpStatus.CONFLICT,"STALE_REVISION","Cruise revision changed");
    }
    private void finish(UUID id){em.flush();jdbc.update("UPDATE \"Cruise\" SET revision=revision+1 WHERE cruise_id=?",id);}
    private void price(BigDecimal amount,String currency) {if(amount==null||amount.signum()<0||currency==null||!currency.matches("[A-Z]{3}"))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"PROGRAM_PRICE_REQUIRED","Explicit nonnegative price and currency required");}
    private void scope(boolean valid){if(!valid)throw new BusinessException(HttpStatus.FORBIDDEN,"CROSS_CRUISE_ACCESS","Object does not belong to requested Cruise");}
    private void stopScope(UUID cruiseId,UUID stopId){scope(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruiseStop\" s JOIN \"Itinerary\" i USING(itinerary_id) WHERE cruise_stop_id=? AND cruise_id=?)",Boolean.class,stopId,cruiseId)));}
    @GetMapping("/packages")
    @Transactional(readOnly=true)
    public ApiResponse<List<Map<String,Object>>> packages(@PathVariable UUID cruiseId){plans.get(cruiseId);return ApiResponse.success(jdbc.queryForList("SELECT p.tour_package_id AS id,p.package_name AS name,p.description,p.package_type::text AS type,p.owner_cruise_id AS \"ownerCruiseId\",p.price,p.currency_code AS currency,p.status::text AS status FROM \"TourPackage\" p JOIN \"CruiseTourPackage\" c USING(tour_package_id) WHERE c.cruise_id=? ORDER BY p.package_name",cruiseId));}
    public record ComponentView(String kind,UUID referenceId,String name,String benefitType) {}
    public record ComponentsView(UUID cruiseId,UUID packageId,List<ComponentView> components) {}
    @GetMapping("/packages/{id}/components")
    @Transactional(readOnly=true)
    public ApiResponse<ComponentsView> components(@PathVariable UUID cruiseId,@PathVariable UUID id){
        plans.get(cruiseId);
        scope(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruiseTourPackage\" WHERE cruise_id=? AND tour_package_id=?)",Boolean.class,cruiseId,id)));
        boolean foreignProgram=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"TourPackageActivity\" l LEFT JOIN \"OnboardActivity\" a USING(onboard_activity_id) WHERE l.tour_package_id=? AND (a.cruise_id IS NULL OR a.cruise_id<>?)) OR EXISTS(SELECT 1 FROM \"TourPackageExcursion\" l LEFT JOIN \"ShoreExcursion\" e USING(shore_excursion_id) LEFT JOIN \"CruiseStop\" s USING(cruise_stop_id) LEFT JOIN \"Itinerary\" i USING(itinerary_id) WHERE l.tour_package_id=? AND (i.cruise_id IS NULL OR i.cruise_id<>?))",Boolean.class,id,cruiseId,id,cruiseId));
        scope(!foreignProgram);
        List<ComponentView> result=new ArrayList<>();
        jdbc.query("SELECT l.service_id,s.service_name,l.benefit_type::text FROM \"TourPackageService\" l JOIN \"Service\" s USING(service_id) WHERE tour_package_id=? ORDER BY s.service_name",rs->{result.add(new ComponentView("services",rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)));},id);
        jdbc.query("SELECT l.onboard_activity_id,a.activity_name,l.benefit_type::text FROM \"TourPackageActivity\" l JOIN \"OnboardActivity\" a USING(onboard_activity_id) WHERE tour_package_id=? ORDER BY a.activity_name",rs->{result.add(new ComponentView("activities",rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)));},id);
        jdbc.query("SELECT l.shore_excursion_id,e.excursion_name,l.benefit_type::text FROM \"TourPackageExcursion\" l JOIN \"ShoreExcursion\" e USING(shore_excursion_id) WHERE tour_package_id=? ORDER BY e.excursion_name",rs->{result.add(new ComponentView("excursions",rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)));},id);
        return ApiResponse.success(new ComponentsView(cruiseId,id,result));
    }
    @GetMapping("/excursions")
    @Transactional(readOnly=true)
    public ApiResponse<List<Map<String,Object>>> excursions(@PathVariable UUID cruiseId){plans.get(cruiseId);return ApiResponse.success(jdbc.queryForList("SELECT e.shore_excursion_id AS id,e.excursion_name AS name,e.maximum_capacity AS capacity,e.price,e.currency_code AS currency,e.cruise_stop_id AS \"cruiseStopId\" FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=? ORDER BY e.start_time",cruiseId));}
    public record ExcursionCommercialInput(@NotNull @Min(1) Integer maximumCapacity,@NotNull @DecimalMin("0") BigDecimal price,@NotBlank @Pattern(regexp="[A-Z]{3}") String currencyCode) {}
    @PatchMapping("/excursions/{id}/commercial")
    public ApiResponse<ShoreExcursionResponse> commercial(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected,@Valid @RequestBody ExcursionCommercialInput input){
        start(cruiseId,expected);
        scope(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE shore_excursion_id=? AND cruise_id=?)",Boolean.class,id,cruiseId)));
        var result=excursions.updateCommercial(id,input.maximumCapacity(),input.price(),input.currencyCode());finish(cruiseId);return ApiResponse.success(result);
    }
    @GetMapping("/activities")
    @Transactional(readOnly=true)
    public ApiResponse<List<ActivityView>> activities(@PathVariable UUID cruiseId){plans.get(cruiseId);return ApiResponse.success(activities.listActivitiesForCruise(cruiseId).stream().map(ActivityView::of).toList());}
    @PostMapping("/activities")
    public ApiResponse<ActivityView> create(@PathVariable UUID cruiseId,@RequestHeader("If-Match") Long expected,@Valid @RequestBody ActivityRequest input){
        start(cruiseId,expected);price(input.price(),input.currencyCode());scope(cruiseId.equals(input.cruiseId()));OnboardActivity a=activity(input);
        ActivityView result=ActivityView.of(activities.createActivity(a,cruiseId,input.deckId(),input.cabinId()));finish(cruiseId);return ApiResponse.success(result);
    }
    @PutMapping("/activities/{id}")
    public ApiResponse<ActivityView> update(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected,@Valid @RequestBody ActivityRequest input){
        start(cruiseId,expected);price(input.price(),input.currencyCode());scope(cruiseId.equals(input.cruiseId()));scope(cruiseId.equals(activities.activity(id).getCruise().getCruiseId()));
        ActivityView result=ActivityView.of(activities.updateActivity(id,activity(input),input.deckId(),input.cabinId()));finish(cruiseId);return ApiResponse.success(result);
    }
    @DeleteMapping("/activities/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected){start(cruiseId,expected);scope(cruiseId.equals(activities.activity(id).getCruise().getCruiseId()));activities.deleteActivity(id);finish(cruiseId);return ApiResponse.success(null);}
    private OnboardActivity activity(ActivityRequest r){OnboardActivity a=new OnboardActivity();a.setActivityName(r.activityName());a.setDescription(r.description());a.setStartTime(r.startTime());a.setEndTime(r.endTime());a.setLocation(r.location());a.setCapacity(r.capacity());a.setPrice(r.price());a.setCurrencyCode(r.currencyCode());return a;}
    @PostMapping("/excursions")
    public ApiResponse<ShoreExcursionResponse> createExcursion(@PathVariable UUID cruiseId,@RequestHeader("If-Match") Long expected,@Valid @RequestBody ShoreExcursionRequest input){start(cruiseId,expected);price(input.price(),input.currencyCode());stopScope(cruiseId,input.cruiseStopId());var result=excursions.create(input);finish(cruiseId);return ApiResponse.success(result);}
    @PutMapping("/excursions/{id}")
    public ApiResponse<ShoreExcursionResponse> updateExcursion(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected,@Valid @RequestBody ShoreExcursionRequest input){start(cruiseId,expected);price(input.price(),input.currencyCode());stopScope(cruiseId,input.cruiseStopId());scope(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE shore_excursion_id=? AND cruise_id=?)",Boolean.class,id,cruiseId)));var result=excursions.update(id,input);finish(cruiseId);return ApiResponse.success(result);}
    @PostMapping("/packages")
    public ApiResponse<PackageView> createPackage(@PathVariable UUID cruiseId,@RequestHeader("If-Match") Long expected,@Valid @RequestBody PackageInput input){
        start(cruiseId,expected);TourPackage p=new TourPackage();p.setOwnerCruiseId(cruiseId);apply(p,input);p=packages.savePackage(p);em.flush();
        jdbc.update("INSERT INTO \"CruiseTourPackage\"(cruise_tour_package_id,cruise_id,tour_package_id,is_active,is_mandatory,entitlement_type,created_at) VALUES(?,?,?,false,false,'OPTIONAL_ADDON',now())",UUID.randomUUID(),cruiseId,p.getTourPackageId());finish(cruiseId);return ApiResponse.success(view(p));
    }
    @PutMapping("/packages/{id}")
    public ApiResponse<PackageView> updatePackage(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected,@Valid @RequestBody PackageInput input){start(cruiseId,expected);TourPackage p=packages.tourPackage(id);scope(cruiseId.equals(p.getOwnerCruiseId()));apply(p,input);p=packages.savePackage(p);finish(cruiseId);return ApiResponse.success(view(p));}
    private void apply(TourPackage p,PackageInput r){p.setPackageName(r.name());p.setDescription(r.description());p.setPackageType(r.type());p.setPrice(r.price());p.setCurrencyCode(r.currency());}
    private PackageView view(TourPackage p){return new PackageView(p.getTourPackageId(),p.getOwnerCruiseId(),p.getPackageName(),p.getStatus().name(),p.getPrice(),p.getCurrencyCode());}
    @PostMapping("/packages/{id}/{kind:services|activities|excursions}")
    public ApiResponse<Void> component(@PathVariable UUID cruiseId,@PathVariable UUID id,@PathVariable String kind,@RequestHeader("If-Match") Long expected,@Valid @RequestBody Component input){
        start(cruiseId,expected);scope(cruiseId.equals(packages.tourPackage(id).getOwnerCruiseId()));
        String duplicateSql=switch(kind){
            case "services" -> "SELECT EXISTS(SELECT 1 FROM \"TourPackageService\" WHERE tour_package_id=? AND service_id=?)";
            case "activities" -> "SELECT EXISTS(SELECT 1 FROM \"TourPackageActivity\" WHERE tour_package_id=? AND onboard_activity_id=?)";
            case "excursions" -> "SELECT EXISTS(SELECT 1 FROM \"TourPackageExcursion\" WHERE tour_package_id=? AND shore_excursion_id=?)";
            default -> throw new IllegalArgumentException("Unknown component kind");
        };
        if(Boolean.TRUE.equals(jdbc.queryForObject(duplicateSql,Boolean.class,id,input.referenceId())))
            throw new BusinessException(HttpStatus.CONFLICT,"PACKAGE_COMPONENT_DUPLICATE","Component already belongs to this package");
        switch(kind){
            case "services" -> packages.addPackageService(id,input.referenceId(),input.benefitType());
            case "activities" -> {scope(cruiseId.equals(activities.activity(input.referenceId()).getCruise().getCruiseId()));packages.addPackageActivity(id,input.referenceId(),input.benefitType());}
            case "excursions" -> {scope(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE shore_excursion_id=? AND cruise_id=?)",Boolean.class,input.referenceId(),cruiseId)));packages.addPackageExcursion(id,input.referenceId(),input.benefitType());}
            default -> throw new IllegalArgumentException("Unknown component kind");
        }finish(cruiseId);return ApiResponse.success(null);
    }
    @PutMapping("/pricing-policy")
    public ApiResponse<com.casms.ship.dto.response.CruisePricingPolicyResponse> policy(@PathVariable UUID cruiseId,@RequestHeader("If-Match") Long expected,@Valid @RequestBody CruisePricingPolicyRequest input,@AuthenticationPrincipal CasmsPrincipal principal){start(cruiseId,expected);var result=pricing.savePricingPolicy(cruiseId,input,principal.getUserId(),false);finish(cruiseId);return ApiResponse.success(result);}
}
