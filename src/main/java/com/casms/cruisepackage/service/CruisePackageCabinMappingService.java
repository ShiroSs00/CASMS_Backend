package com.casms.cruisepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackageContracts.Blocker;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.repository.ShipRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Explicit, Ship-scoped ID mapping; CabinType/name/amenities never prove compatibility. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CruisePackageCabinMappingService {
    private final JdbcTemplate jdbc;
    private final ShipRepository ships;
    private final org.springframework.beans.factory.ObjectProvider<com.casms.booking.service.BookingService> bookings;
    public record Category(UUID id,UUID templateVersionId,String name,String packageName) {}
    public record Selection(@NotNull UUID logicalCategoryId,@NotEmpty List<@NotNull UUID> cabinIds) {}
    public record Confirmation(UUID expectedMappingId,@NotBlank String expectedStructureFingerprint,
                               @NotEmpty List<@NotNull @Valid Selection> assignments) {}
    public record ConfirmationView(UUID id,long revision,UUID confirmedBy,Instant confirmedAt,String status) {}
    public record View(UUID shipId,String structureFingerprint,ConfirmationView confirmation,
                       List<Category> categories,List<Map<String,Object>> cabins,List<Map<String,Object>> assignments) {}
    public record Assessment(UUID mappingId,List<UUID> cabinIds,List<Blocker> blockers,Long revision,UUID confirmedBy,Instant confirmedAt) {
        public Assessment(UUID id,List<UUID> cabins,List<Blocker> blockers){this(id,cabins,blockers,null,null,null);}
    }

    public List<Category> categories(UUID versionId) {
        return jdbc.query("SELECT c.logical_category_id,vc.template_version_id,c.display_name,v.name FROM \"CruisePackageLogicalCategory\" c JOIN \"CruisePackageVersionCategory\" vc USING(logical_category_id) JOIN \"FarePackageTemplateVersion\" v USING(template_version_id) WHERE ((?::uuid IS NULL AND v.status IN ('PUBLISHED','SUPERSEDED')) OR vc.template_version_id=?) ORDER BY v.name,c.display_name,c.logical_category_id",
                (r,n)->new Category(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4)),versionId,versionId);
    }
    public Category requireCategory(UUID categoryId,UUID versionId) {
        if(categoryId==null)throw error("CABIN_CATEGORY_ID_REQUIRED","Cần chọn hạng thương mại bằng ID ổn định; không tự đối chiếu tên hoặc enum.");
        return categories(versionId).stream().filter(c->c.id().equals(categoryId)).findFirst()
                .orElseThrow(()->error("CABIN_CATEGORY_REFERENCE_INVALID","Hạng thương mại không thuộc phiên bản mẫu đã chọn."));
    }
    public void createCategories(UUID versionId,UUID templateId,List<String> names,List<UUID> reusedIds) {
        Map<String,UUID> reused=new HashMap<>();
        for(UUID id:reusedIds==null?List.<UUID>of():reusedIds){
            var rows=jdbc.queryForList("SELECT display_name FROM \"CruisePackageLogicalCategory\" WHERE logical_category_id=?",String.class,id);
            if(rows.size()!=1||!names.contains(rows.getFirst())||reused.putIfAbsent(rows.getFirst(),id)!=null)
                throw error("CABIN_CATEGORY_REFERENCE_INVALID","ID tái sử dụng phải tồn tại trong Cruise Package domain, không trùng và có tên đã khai báo trong phiên bản mới.");
        }
        for(String name:names){
            UUID id=reused.get(name);
            if(id==null){id=UUID.randomUUID();jdbc.update("INSERT INTO \"CruisePackageLogicalCategory\"(logical_category_id,origin_template_id,display_name) VALUES(?,?,?)",id,templateId,name);}
            jdbc.update("INSERT INTO \"CruisePackageVersionCategory\"(logical_category_id,template_version_id) VALUES(?,?)",id,versionId);
        }
    }
    private List<Map<String,Object>> structure(UUID shipId) {
        return jdbc.queryForList("""
                SELECT c.cabin_id,c.ship_id,c.deck_id,c.capacity,c.minimum_occupancy,
                       to_jsonb(c)-ARRAY['status','created_at','updated_at'] AS cabin_structure,
                       d.ship_id AS deck_ship_id,d.deck_number,d.service_level,
                       to_jsonb(d)-ARRAY['created_at','updated_at','price_multiplier'] AS deck_structure
                FROM "Cabin" c LEFT JOIN "Deck" d USING(deck_id) WHERE c.ship_id=? ORDER BY c.cabin_id
                """,shipId);
    }
    public String structureFingerprint(UUID shipId) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((shipId+":"+structure(shipId)).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private Map<String,Object> latest(UUID shipId) {
        var rows=jdbc.queryForList("SELECT * FROM \"ShipCruisePackageCabinMapping\" WHERE ship_id=? ORDER BY mapping_revision DESC LIMIT 1",shipId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public View view(UUID shipId) {
        if(!ships.existsById(shipId))throw error("SHIP_NOT_FOUND","Không tìm thấy tàu cần đối chiếu mapping.");
        var current=latest(shipId);
        var cabins=jdbc.queryForList("SELECT c.cabin_id,c.cabin_number,c.capacity,c.deck_id,d.deck_name,c.status::text FROM \"Cabin\" c JOIN \"Deck\" d USING(deck_id) WHERE c.ship_id=? AND d.ship_id=? ORDER BY d.deck_number,c.cabin_number",shipId,shipId);
        var assignments=current==null?List.<Map<String,Object>>of():jdbc.queryForList("SELECT logical_category_id,cabin_id,cabin_id_snapshot FROM \"ShipCruisePackageCabinMappingItem\" WHERE mapping_id=? ORDER BY logical_category_id,cabin_id_snapshot",current.get("mapping_id"));
        String fingerprint=structureFingerprint(shipId);
        var confirmation=current==null?null:new ConfirmationView((UUID)current.get("mapping_id"),((Number)current.get("mapping_revision")).longValue(),
                (UUID)current.get("confirmed_by"),((java.sql.Timestamp)current.get("confirmed_at")).toInstant(),Objects.equals(fingerprint,current.get("structure_fingerprint"))?"CURRENT":"STALE");
        return new View(shipId,fingerprint,confirmation,categories(null),cabins,assignments);
    }
    @Transactional
    public View confirm(UUID shipId,Confirmation input,CasmsPrincipal actor) {
        if(actor==null||!actor.hasRole(Role.SHIP_MANAGER)||!actor.hasAuthority("SHIP_CABIN_CATEGORY_MAPPING_CONFIRM"))
            throw new org.springframework.security.access.AccessDeniedException("Ship Manager mapping confirmation permission required");
        lockShip(shipId);
        var current=latest(shipId);
        if(!Objects.equals(input.expectedMappingId(),current==null?null:current.get("mapping_id"))
                ||!Objects.equals(input.expectedStructureFingerprint(),structureFingerprint(shipId)))
            throw error("STALE_REVISION","Mapping hoặc cấu trúc tàu đã đổi; tải lại và xác nhận đúng dữ liệu hiện tại.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "BookingPassenger" p JOIN "Booking" b USING(booking_id) JOIN "Cruise" c USING(cruise_id)
                 WHERE c.ship_id=? AND c.status NOT IN ('CANCELLED','COMPLETED') AND p.fare_offer_id IS NOT NULL
                  AND p.participation_status='Active' AND (b.booking_status='Confirmed' OR (b.booking_status='Draft' AND (b.hold_expires_at IS NULL OR b.hold_expires_at>now()))))
                """,Boolean.class,shipId)))
            throw error("CABIN_MAPPING_BOOKING_DEPENDENCY","Có giữ chỗ hoặc Booking gói du thuyền đang hiệu lực; chưa chứng minh an toàn khi thay revision mapping. Không tự chuyển hạng/cabin đã mua.");
        var cabins=structure(shipId);Set<UUID> seenCabins=new HashSet<>(),seenCategories=new HashSet<>();
        for(var selection:input.assignments()) {
            if(!seenCategories.add(selection.logicalCategoryId()))throw error("CABIN_MAPPING_DUPLICATE","Hạng thương mại bị lặp trong revision mapping.");
            var category=categories(null).stream().filter(c->c.id().equals(selection.logicalCategoryId())).findFirst()
                    .orElseThrow(()->error("CABIN_CATEGORY_REFERENCE_INVALID","Không tìm thấy ID hạng thương mại."));
            String state=jdbc.queryForObject("SELECT status FROM \"FarePackageTemplateVersion\" WHERE template_version_id=?",String.class,category.templateVersionId());
            if(!Set.of("PUBLISHED","SUPERSEDED").contains(state))throw error("TEMPLATE_NOT_PUBLISHED","Chỉ xác nhận mapping cho phiên bản đã phát hành.");
            for(UUID cabinId:selection.cabinIds()) {
                if(!seenCabins.add(cabinId))throw error("CABIN_MAPPING_DUPLICATE","Một cabin vật lý chỉ thuộc một hạng thương mại trong revision mapping.");
                var cabin=cabins.stream().filter(c->cabinId.equals(c.get("cabin_id"))).findFirst()
                        .orElseThrow(()->error("CABIN_MAPPING_WRONG_SHIP","Cabin không tồn tại trên đúng tàu."));
                if(!shipId.equals(cabin.get("deck_ship_id"))||Boolean.TRUE.equals(cabin.get("service_level"))
                        ||!(cabin.get("deck_number") instanceof Number deck)||deck.intValue()<1
                        ||!(cabin.get("capacity") instanceof Number n)||n.intValue()<1)
                    throw error("CABIN_STRUCTURE_INVALID","Cabin phải có Deck thuộc đúng Ship và sức chứa cấu trúc hợp lệ.");
            }
        }
        UUID mappingId=UUID.randomUUID();long revision=current==null?1:((Number)current.get("mapping_revision")).longValue()+1;
        jdbc.update("INSERT INTO \"ShipCruisePackageCabinMapping\"(mapping_id,ship_id,mapping_revision,structure_fingerprint,confirmed_by,confirmed_at) VALUES(?,?,?,?,?,?)",mappingId,shipId,revision,input.expectedStructureFingerprint(),actor.getUserId(),java.sql.Timestamp.from(Instant.now()));
        for(var selection:input.assignments())for(UUID cabinId:selection.cabinIds())
            jdbc.update("INSERT INTO \"ShipCruisePackageCabinMappingItem\"(mapping_id,logical_category_id,cabin_id,cabin_id_snapshot) VALUES(?,?,?,?)",mappingId,selection.logicalCategoryId(),cabinId,cabinId);
        return view(shipId);
    }
    public Assessment assess(UUID shipId,UUID categoryId,UUID versionId) {
        if(categoryId==null)return new Assessment(null,List.of(),List.of(new Blocker("CABIN_CATEGORY_ID_REQUIRED","Chưa có ID ổn định cho hạng thương mại; không suy luận từ tên/enum.",versionId)));
        try{requireCategory(categoryId,versionId);}catch(BusinessException e){return new Assessment(null,List.of(),List.of(new Blocker("CABIN_CATEGORY_REFERENCE_INVALID","Hạng thương mại không thuộc phiên bản mẫu đã bán.",categoryId)));}
        var current=latest(shipId);
        if(current==null)return new Assessment(null,List.of(),List.of(new Blocker("CABIN_MAPPING_CONFIRMATION_REQUIRED","Quản lý tàu cần xác nhận mapping hạng thương mại tới cabin vật lý.",shipId)));
        UUID id=(UUID)current.get("mapping_id");
        if(!Objects.equals(current.get("structure_fingerprint"),structureFingerprint(shipId)))
            return new Assessment(id,List.of(),List.of(new Blocker("CABIN_MAPPING_STALE","Cấu trúc hoặc sức chứa cabin đã thay đổi sau xác nhận mapping.",id)));
        var cabins=jdbc.queryForList("SELECT cabin_id FROM \"ShipCruisePackageCabinMappingItem\" WHERE mapping_id=? AND logical_category_id=? AND cabin_id IS NOT NULL ORDER BY cabin_id",UUID.class,id,categoryId);
        if(cabins.isEmpty())return new Assessment(id,List.of(),List.of(new Blocker("CABIN_MAPPING_MISSING","Revision mapping hiện hành chưa ánh xạ cabin cho hạng thương mại này.",categoryId)));
        return new Assessment(id,List.copyOf(cabins),List.of(),((Number)current.get("mapping_revision")).longValue(),
                (UUID)current.get("confirmed_by"),((java.sql.Timestamp)current.get("confirmed_at")).toInstant());
    }
    public boolean hasInventory(UUID cruiseId,List<UUID> cabinIds) {
        return !cabinIds.isEmpty()&&bookings.getObject().hasMappedCabinInventory(cruiseId,Set.copyOf(cabinIds));
    }
    public Set<UUID> requirePurchasedCabins(com.casms.cruisepackage.dto.CruisePackagePurchaseSelection selection) {
        if(selection==null||selection.logicalCategoryId()==null||selection.cabinMappingId()==null||selection.physicalCabinIds().isEmpty())
            throw error("CABIN_MAPPING_PURCHASE_EVIDENCE_REQUIRED","Nội dung mua chưa có bằng chứng mapping bằng ID; không tự suy luận hoặc backfill từ hạng cabin cũ.");
        var assessment=assess(selection.shipId(),selection.logicalCategoryId(),selection.templateVersionId());
        if(!assessment.blockers().isEmpty()||!Objects.equals(assessment.mappingId(),selection.cabinMappingId())
                ||!new HashSet<>(assessment.cabinIds()).equals(new HashSet<>(selection.physicalCabinIds())))
            throw error("CABIN_MAPPING_STALE","Mapping đã thay đổi so với nội dung đã chọn/mua; không tự đổi cabin hoặc quyền lợi.");
        return Set.copyOf(assessment.cabinIds());
    }
    @Transactional public void lockShip(UUID shipId){
        ships.findByIdForUpdate(shipId).orElseThrow(()->error("SHIP_NOT_FOUND","Không tìm thấy tàu."));
        jdbc.queryForList("SELECT cabin_id FROM \"Cabin\" WHERE ship_id=? ORDER BY cabin_id FOR SHARE",UUID.class,shipId);
    }
    public String evidence(UUID shipId) {var current=latest(shipId);return current==null?"NONE":current.get("mapping_id")+":"+current.get("mapping_revision")+":"+structureFingerprint(shipId);}
    private BusinessException error(String code,String message){return new BusinessException(HttpStatus.CONFLICT,code,message);}
}
