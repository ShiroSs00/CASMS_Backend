package com.casms.cruisepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackageContracts;
import com.casms.cruisepackage.entity.CruisePackageAddonOffer;
import com.casms.cruisepackage.repository.CruisePackageRepository;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseMainflowService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CruisePackageAddonService {
    private final CruisePackageRepository repository;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final CruiseMainflowService plans;
    public enum Stage {BOOKING,PRE_DEPARTURE,ONBOARD}
    public record Window(@NotNull Stage stage,@NotNull Instant startsAt,@NotNull Instant endsAt,@NotNull Instant operationalCutoff) {}
    public record Input(@NotNull CruisePackageContracts.ResourceType resourceType,@NotNull UUID resourceId,
            @NotNull @DecimalMin("0") @Digits(integer=15,fraction=4) BigDecimal amount,
            @NotEmpty List<@NotNull @Valid Window> windows,@NotBlank @Size(max=4000) String eligibility) {}
    public record Candidate(CruisePackageContracts.ResourceType resourceType, UUID resourceId,
            String name, String unitCode, Instant cutoffLimit, boolean eligible, String blocker, List<Stage> allowedStages) {}
    public record Configuration(UUID cruiseId, UUID shipId, boolean canCreate, String reason,
            Instant departureAt, Instant returnAt, Instant bookingOpensAt, Instant bookingClosesAt,
            List<Candidate> candidates) {}
    /** Advisory read; writes re-run resource and window validation under the existing voyage lock. */
    public Configuration configuration(UUID cruiseId) {
        maker();
        var rows = jdbc.queryForList("SELECT ship_id,status,departure_datetime,return_datetime,booking_opens_at,booking_closes_at FROM \"Cruise\" WHERE cruise_id=?", cruiseId);
        if (rows.isEmpty()) throw error("CRUISE_NOT_FOUND", "Không tìm thấy chuyến khởi hành.");
        var cruise = rows.getFirst();
        UUID shipId = (UUID) cruise.get("ship_id");
        boolean editable = Set.of("DRAFT", "PUBLISHED").contains(cruise.get("status"));
        String reason = !editable ? "Trạng thái chuyến không cho phép tạo cấu hình Add-on."
                : shipId == null ? "Cần phân công tàu trước khi cấu hình Add-on." : null;
        List<Candidate> candidates = new ArrayList<>();
        if (shipId != null) {
            var sources = jdbc.queryForList("SELECT 'ACTIVITY' AS kind,onboard_activity_id AS id,activity_name AS name FROM \"OnboardActivity\" WHERE cruise_id=? UNION ALL SELECT 'EXCURSION',e.shore_excursion_id,e.excursion_name FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" p USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=? UNION ALL SELECT 'SERVICE',s.service_id,s.service_name FROM \"Service\" s WHERE EXISTS(SELECT 1 FROM \"DeckServiceAssignment\" a JOIN \"Deck\" d USING(deck_id) WHERE a.service_id=s.service_id AND d.ship_id=?) ORDER BY kind,name,id", cruiseId, cruiseId, shipId);
            for (var source : sources) {
                var type = CruisePackageContracts.ResourceType.valueOf((String) source.get("kind"));
                UUID id = (UUID) source.get("id");
                try {
                    var resource = resource(type, id, cruiseId, shipId);
                    candidates.add(new Candidate(type, id, (String) resource.get("name"),
                            (String) resource.get("unit_code"), instant(resource.get("cutoff_limit")), true, null, allowedStages(resource)));
                } catch (BusinessException blocked) {
                    candidates.add(new Candidate(type, id, (String) source.get("name"), null, null,
                            false, blocked.getCode() + ": " + blocked.getMessage(), List.of()));
                }
            }
        }
        return new Configuration(cruiseId, shipId, reason == null, reason,
                instant(cruise.get("departure_datetime")), instant(cruise.get("return_datetime")),
                instant(cruise.get("booking_opens_at")), instant(cruise.get("booking_closes_at")), candidates);
    }
    private Instant instant(Object value) {
        return value instanceof java.sql.Timestamp t ? t.toInstant()
                : value instanceof java.time.OffsetDateTime t ? t.toInstant() : null;
    }
    public List<CruisePackageAddonOffer> list(UUID cruiseId){return em.createQuery("from CruisePackageAddonOffer where cruiseId=:id order by createdAt,id",CruisePackageAddonOffer.class).setParameter("id",cruiseId).getResultList();}
    private UUID maker(){var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!(auth.getPrincipal() instanceof CasmsPrincipal actor)||!actor.hasRole(Role.ACTIVITY_SERVICE_MANAGER)||!actor.hasAuthority("TOUR_PACKAGE_MANAGE"))throw new org.springframework.security.access.AccessDeniedException("ASM Add-on configuration permission required");return actor.getUserId();}
    @Transactional public CruisePackageAddonOffer create(UUID cruiseId,Input input){
        UUID actor=maker();plans.lockOperational(cruiseId);
        UUID shipId=jdbc.queryForObject("SELECT ship_id FROM \"Cruise\" WHERE cruise_id=? AND status IN ('DRAFT','PUBLISHED')",UUID.class,cruiseId);
        if(shipId==null)throw error("CRUISE_SHIP_REQUIRED","Cần phân công tàu trước khi cấu hình Add-on.");
        var resource=resource(input.resourceType(),input.resourceId(),cruiseId,shipId);
        validateWindows(cruiseId,input.windows(),resource);
        var offer=new CruisePackageAddonOffer();offer.setId(UUID.randomUUID());offer.setCruiseId(cruiseId);offer.setShipId(shipId);
        switch(input.resourceType()){case ACTIVITY->offer.setOnboardActivityId(input.resourceId());case EXCURSION->offer.setShoreExcursionId(input.resourceId());case SERVICE->offer.setServiceId(input.resourceId());}
        offer.setResourceName((String)resource.get("name"));offer.setAmount(input.amount());offer.setCurrencyCode("USD");
        offer.setPricingUnit(input.resourceType()==CruisePackageContracts.ResourceType.SERVICE?"SERVICE_UNIT":"PERSON");
        offer.setServiceUnitCode(input.resourceType()==CruisePackageContracts.ResourceType.SERVICE?(String)resource.get("unit_code"):null);
        offer.setSalesWindows(input.windows().stream().map(w->Map.of("stage",w.stage().name(),"startsAt",w.startsAt().toString(),"endsAt",w.endsAt().toString(),"operationalCutoff",w.operationalCutoff().toString())).toList());
        offer.setEligibility(input.eligibility());offer.setCreatedBy(actor);offer.setCreatedAt(Instant.now());repository.insert(offer);repository.flush();return offer;
    }
    @Transactional public CruisePackageAddonOffer publish(UUID cruiseId,UUID id,Long expected){
        UUID actor=maker();plans.lockOperational(cruiseId);var offer=repository.find(CruisePackageAddonOffer.class,id,true);
        if(offer==null||!offer.getCruiseId().equals(cruiseId))throw error("ADDON_OFFER_NOT_FOUND","Không tìm thấy Add-on của chuyến.");
        if(expected==null||expected!=offer.getRevision())throw error("STALE_REVISION","Revision Add-on đã thay đổi.");
        UUID ship=jdbc.queryForObject("SELECT ship_id FROM \"Cruise\" WHERE cruise_id=?",UUID.class,cruiseId);
        if(!offer.getShipId().equals(ship))throw error("SHIP_CHANGED","Tàu đã đổi; không tự chuyển Add-on.");
        var type=offer.getServiceId()!=null?CruisePackageContracts.ResourceType.SERVICE:offer.getOnboardActivityId()!=null?CruisePackageContracts.ResourceType.ACTIVITY:CruisePackageContracts.ResourceType.EXCURSION;
        UUID resourceId=offer.getServiceId()!=null?offer.getServiceId():offer.getOnboardActivityId()!=null?offer.getOnboardActivityId():offer.getShoreExcursionId();
        var resource=resource(type,resourceId,cruiseId,ship);
        if(type==CruisePackageContracts.ResourceType.SERVICE&&!Objects.equals(offer.getServiceUnitCode(),resource.get("unit_code")))throw error("BENEFIT_USAGE_UNIT_UNSUPPORTED","Đơn vị dịch vụ đã thay đổi; không tự quy đổi.");
        validateWindows(cruiseId,offer.getSalesWindows().stream().map(w->new Window(Stage.valueOf(w.get("stage")),Instant.parse(w.get("startsAt")),Instant.parse(w.get("endsAt")),Instant.parse(w.get("operationalCutoff")))).toList(),resource);
        if(offer.getPublishedAt()!=null)return offer;
        offer.setPublishedBy(actor);offer.setPublishedAt(Instant.now());repository.flush();return offer;
    }
    private Map<String,Object> resource(CruisePackageContracts.ResourceType type,UUID id,UUID cruiseId,UUID shipId){
        String sql=switch(type){
            case ACTIVITY->"SELECT a.activity_name AS name,a.price,a.currency_code,a.end_time AS cutoff_limit,NULL::text AS unit_code FROM \"OnboardActivity\" a WHERE a.onboard_activity_id=? AND a.cruise_id=? AND a.status IN ('PLANNED','OPEN','FULL')";
            case EXCURSION->"SELECT e.excursion_name AS name,e.price,e.currency_code,e.planned_return_time AS cutoff_limit,NULL::text AS unit_code FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" p USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) JOIN \"Supplier\" s USING(supplier_id) WHERE e.shore_excursion_id=? AND i.cruise_id=? AND e.status IN ('PLANNED','OPEN','FULL') AND s.status='ACTIVE' AND s.verification_status='VERIFIED'";
            case SERVICE->"SELECT s.service_name AS name,s.price,s.currency_code,s.can_buy_pre_cruise,s.can_buy_onboard,CASE WHEN s.requires_schedule THEN (SELECT max(w.ends_at) FROM \"ServiceSchedule\" w WHERE w.service_id=s.service_id AND w.cruise_id=c.cruise_id AND w.status='OPEN') ELSE c.return_datetime END AS cutoff_limit,s.unit_code FROM \"Service\" s JOIN \"Cruise\" c ON c.cruise_id=? WHERE s.service_id=? AND s.status='ACTIVE' AND EXISTS(SELECT 1 FROM \"DeckServiceAssignment\" a JOIN \"Deck\" d USING(deck_id) WHERE a.service_id=s.service_id AND d.ship_id=c.ship_id)";
        };
        var rows=type==CruisePackageContracts.ResourceType.SERVICE?jdbc.queryForList(sql,cruiseId,id):jdbc.queryForList(sql,id,cruiseId);
        if(rows.size()!=1)throw error("ADDON_RESOURCE_INVALID","Add-on phải tham chiếu đúng hoạt động/tour/dịch vụ hợp lệ của chuyến và tàu hiện tại.");
        var row=rows.getFirst();
        if(row.get("cutoff_limit")==null)throw error("ADDON_OPERATIONAL_SCHEDULE_REQUIRED","Dịch vụ cần lịch phục vụ nhưng chưa có lịch hiện hành của chuyến.");
        if(type==CruisePackageContracts.ResourceType.SERVICE&&(row.get("unit_code")==null||row.get("unit_code").toString().isBlank()))throw error("BENEFIT_USAGE_UNIT_UNSUPPORTED","Dịch vụ chưa có unitCode authoritative.");
        return row;
    }
    private List<Stage> allowedStages(Map<String,Object> resource) {
        if (!resource.containsKey("can_buy_pre_cruise")) return List.of(Stage.values());
        var stages = new ArrayList<Stage>();
        if (Boolean.TRUE.equals(resource.get("can_buy_pre_cruise")))
            stages.addAll(List.of(Stage.BOOKING, Stage.PRE_DEPARTURE));
        if (Boolean.TRUE.equals(resource.get("can_buy_onboard"))) stages.add(Stage.ONBOARD);
        return List.copyOf(stages);
    }

    private void validateWindows(UUID cruiseId,List<Window> windows,Map<String,Object> resource){
        Set<Stage> stages=new HashSet<>();Instant limit=((java.sql.Timestamp)resource.get("cutoff_limit")).toInstant();
        var cruise=jdbc.queryForMap("SELECT departure_datetime,return_datetime,booking_opens_at,booking_closes_at FROM \"Cruise\" WHERE cruise_id=?",cruiseId);
        Instant departure=((java.sql.Timestamp)cruise.get("departure_datetime")).toInstant(),returnAt=((java.sql.Timestamp)cruise.get("return_datetime")).toInstant();
        for(var w:windows){
            if (resource.containsKey("can_buy_pre_cruise")) {
                boolean allowed = Boolean.TRUE.equals(resource.get(w.stage()==Stage.ONBOARD
                        ? "can_buy_onboard" : "can_buy_pre_cruise"));
                if (!allowed) throw error("ADDON_SERVICE_STAGE_DISABLED",
                        "Dịch vụ chưa cho phép bán trong giai đoạn đã chọn.");
            }
            if(w.stage()==Stage.PRE_DEPARTURE&&w.endsAt().isAfter(departure)||w.stage()==Stage.ONBOARD&&(w.startsAt().isBefore(departure)||w.endsAt().isAfter(returnAt)))
                throw error("ADDON_SALES_WINDOW_INVALID","Thời hạn giai đoạn không phù hợp ngày khởi hành/trở về thực tế.");
            if(w.stage()==Stage.BOOKING){
                if(!(cruise.get("booking_opens_at") instanceof java.sql.Timestamp open)||!(cruise.get("booking_closes_at") instanceof java.sql.Timestamp close)
                        ||w.startsAt().isBefore(open.toInstant())||w.endsAt().isAfter(close.toInstant()))
                    throw error("ADDON_SALES_WINDOW_INVALID","Giai đoạn BOOKING phải nằm trong khoảng nhận đặt chỗ hiện hành đã cấu hình; không tự đặt thời hạn.");
            }
        }

        for(var w:windows)if(!stages.add(w.stage())||!w.startsAt().isBefore(w.endsAt())||w.endsAt().isAfter(w.operationalCutoff())||w.operationalCutoff().isAfter(limit))
            throw error("ADDON_SALES_WINDOW_INVALID","Khai báo mỗi giai đoạn một lần, bắt đầu < kết thúc ≤ operational cutoff và cutoff không sau lịch phục vụ thực tế. Không áp thời hạn mặc định.");
    }
    private BusinessException error(String code,String message){return new BusinessException(HttpStatus.CONFLICT,code,message);}
}
