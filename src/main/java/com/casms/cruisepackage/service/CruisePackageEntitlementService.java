package com.casms.cruisepackage.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackageContracts.Blocker;
import com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType;
import com.casms.cruisepackage.entity.BookingCruisePackageBenefitSnapshot;
import com.casms.cruisepackage.repository.CruisePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Grant identity is the immutable purchased-benefit row. Existing Participation/ServiceUsage are the usage ledger. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CruisePackageEntitlementService {
    private final CruisePackageRepository repository;
    private final CruisePackageBenefitUsagePolicy policy;
    private final JdbcTemplate jdbc;

    public record EntitlementView(UUID grantId,UUID bookingPassengerId,String benefitName,String inclusion,
            String sourcePackageName,Instant grantedAt,String status,String usagePolicy,String usageUnit,String serviceUnitCode,
            Long grantedQuantity,Long usedQuantity,Long remainingQuantity,Boolean requiresReservation,
            Instant usageStartsAt,Instant usageEndsAt,List<Map<String,Object>> occurrences,
            List<Map<String,Object>> registrations,List<Blocker> blockers,boolean canRegister) {}

    public List<EntitlementView> list(UUID bookingId) {
        return jdbc.queryForList("SELECT booking_passenger_id FROM \"BookingFareSnapshot\" WHERE booking_id=? ORDER BY booking_passenger_id",UUID.class,bookingId)
                .stream().flatMap(id->repository.purchasedBenefits(id).stream()).map(this::view).toList();
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record OperationEntitlement(UUID operationId,EntitlementView entitlement) {}
    public EntitlementView operationGrant(UUID grantId,UUID passengerId) {
        if(grantId==null)return null;
        var grant=repository.find(BookingCruisePackageBenefitSnapshot.class,grantId,false);
        if(grant==null)throw business("ENTITLEMENT_NOT_FOUND","Không tìm thấy quyền lợi của operation.");
        if(!grant.getBookingPassengerId().equals(passengerId))throw business("ENTITLEMENT_PASSENGER_MISMATCH","Grant không khớp hành khách trong operation.");
        return view(grant);
    }

    public void validateServiceWindow(UUID grantId,com.casms.servicepackage.entity.ServiceSchedule schedule) {
        if(grantId==null)return;
        var grant=repository.find(BookingCruisePackageBenefitSnapshot.class,grantId,false);
        if(grant==null)throw business("ENTITLEMENT_NOT_FOUND","Không tìm thấy grant cho lịch phục vụ.");
        if(grant.getUsageStartsAt()==null)return;
        Instant start=schedule==null?Instant.now():schedule.getStartsAt(),end=schedule==null?start:schedule.getEndsAt();
        if(start.isBefore(grant.getUsageStartsAt())||end.isAfter(grant.getUsageEndsAt()))
            throw business("BENEFIT_USAGE_WINDOW_INVALID","Lịch phục vụ nằm ngoài khoảng sử dụng đã mua.");
    }

    /** Underlying service has already verified the authenticated owner/staff and locked the operational resource. */
    @Transactional
    public UUID reserve(BookingPassenger passenger,ResourceType type,UUID resourceId,int quantity) {
        if(passenger.getFareOfferId()==null)return null;
        var matches=repository.purchasedBenefits(passenger.getBookingPassengerId()).stream()
                .filter(b->matches(b,type,resourceId)).toList();
        if(matches.isEmpty())throw business("ADDON_PURCHASE_REQUIRED","Không có quyền lợi đã mua cho lần phục vụ này; cần giao dịch mua thêm hợp lệ.");
        if(matches.size()!=1)throw business("ENTITLEMENT_AMBIGUOUS","Có nhiều quyền lợi trỏ cùng lần phục vụ; cần đối chiếu cấu hình, không tự chọn grant.");
        var grant=require(matches.getFirst().getId(),passenger.getBookingPassengerId(),type,resourceId,true);
        if(type!=ResourceType.SERVICE&&quantity!=1)
            throw business("ENTITLEMENT_PASSENGER_MAPPING_REQUIRED","Mỗi hành khách đăng ký quyền lợi của chính mình; không dùng quantity đăng ký để cấp quyền cho người khác.");
        requireQuantity(grant,type==ResourceType.SERVICE?quantity:1);
        requireOperationalTime(grant,type,resourceId,false,null);
        if (type == ResourceType.SERVICE) requirePendingServiceQuantity(grant, quantity, null);
        return grant.getId();
    }

    @Transactional
    public void fulfill(UUID grantId,BookingPassenger passenger,ResourceType type,UUID resourceId,
            int fulfilledQuantity,String serviceUnitCode,Instant occurredAt) {
        if(passenger.getFareOfferId()==null&&grantId==null)return;
        if(grantId==null)throw business("ENTITLEMENT_NOT_FOUND","Yêu cầu phục vụ chưa gắn quyền lợi đã mua của hành khách.");
        var grant=require(grantId,passenger.getBookingPassengerId(),type,resourceId,true);
        if(type==ResourceType.SERVICE&&(!Objects.equals(grant.getServiceUnitCode(),serviceUnitCode)||serviceUnitCode==null))
            throw business("BENEFIT_USAGE_UNIT_UNSUPPORTED","Đơn vị fulfillment phải khớp chính xác với đơn vị dịch vụ trong snapshot quyền lợi.");
        if(type!=ResourceType.SERVICE&&fulfilledQuantity!=1)
            throw business("ENTITLEMENT_PASSENGER_MAPPING_REQUIRED","Một hành khách hoàn thành một session ghi nhận một PARTICIPATION.");
        requireQuantity(grant,fulfilledQuantity);
        requireOperationalTime(grant,type,resourceId,true,occurredAt);
    }

    /** Revalidate a pending operation without consuming anything. */
    @Transactional
    public void validateReservation(UUID grantId,BookingPassenger passenger,ResourceType type,UUID resourceId,int quantity) {
        if(passenger.getFareOfferId()==null&&grantId==null)return;
        if(grantId==null)throw business("ENTITLEMENT_NOT_FOUND","Đăng ký/yêu cầu chưa gắn quyền lợi đã mua.");
        var grant=require(grantId,passenger.getBookingPassengerId(),type,resourceId,true);
        if(type!=ResourceType.SERVICE&&quantity!=1)throw business("ENTITLEMENT_PASSENGER_MAPPING_REQUIRED","Đăng ký phải ánh xạ riêng cho từng hành khách.");
        requireQuantity(grant,type==ResourceType.SERVICE?quantity:1);
        requireOperationalTime(grant,type,resourceId,false,null);
    }

    @Transactional
    public void validateServiceReservation(UUID grantId, BookingPassenger passenger, UUID serviceId, int quantity, UUID excludeRequestId) {
        var grant = require(grantId, passenger.getBookingPassengerId(), ResourceType.SERVICE, serviceId, true);
        requireQuantity(grant, quantity);
        requirePendingServiceQuantity(grant, quantity, excludeRequestId);
    }

    private void requirePendingServiceQuantity(BookingCruisePackageBenefitSnapshot grant, int quantity, UUID excludeRequestId) {
        if (!"LIMITED_INCLUDED".equals(grant.getInclusion())) return;
        Long pending = jdbc.queryForObject("SELECT COALESCE(sum(quantity),0) FROM \"ServiceRequest\" WHERE fare_benefit_snapshot_id=? AND status IN ('REQUESTED','ACCEPTED','IN_PROGRESS') AND (?::uuid IS NULL OR service_request_id<>?::uuid)", Long.class, grant.getId(), excludeRequestId, excludeRequestId);
        if (pending == null || pending + used(grant) + quantity > grant.getQuantityLimit())
            throw business("ENTITLEMENT_EXHAUSTED", "Quota includes pending service reservations; cancel an unused reservation before registering more.");
    }

    private BookingCruisePackageBenefitSnapshot require(UUID grantId,UUID passengerId,ResourceType type,UUID resourceId,boolean lock) {
        // Booking state is stable through the operation. Cancellation uses the existing exclusive Booking lock.
        var owners=jdbc.queryForList("SELECT b.booking_id FROM \"Booking\" b JOIN \"BookingPassenger\" p USING(booking_id) WHERE p.booking_passenger_id=? FOR SHARE OF b",UUID.class,passengerId);
        if(owners.isEmpty())throw business("ENTITLEMENT_PASSENGER_MISMATCH","Không tìm thấy hành khách của quyền lợi.");
        var grant=repository.find(BookingCruisePackageBenefitSnapshot.class,grantId,lock);
        if(grant==null)throw business("ENTITLEMENT_NOT_FOUND","Không tìm thấy quyền lợi đã mua.");
        if(!grant.getBookingPassengerId().equals(passengerId))throw business("ENTITLEMENT_PASSENGER_MISMATCH","Quyền lợi không thuộc hành khách đang sử dụng.");
        if(!matches(grant,type,resourceId))throw business("ENTITLEMENT_RESOURCE_MISMATCH","Lần phục vụ không nằm trong snapshot quyền lợi đã mua.");
        if("OPTIONAL".equals(grant.getInclusion()))throw business("ADDON_PURCHASE_REQUIRED","Quyền lợi tùy chọn cần Add-on Purchase đã xác nhận; không cấp quyền miễn phí.");
        if("NOT_INCLUDED".equals(grant.getInclusion()))throw business("BENEFIT_NOT_INCLUDED","Quyền lợi không bao gồm, không được sử dụng miễn phí.");
        var issues=blockers(grant);
        if(!issues.isEmpty())throw new BusinessException(HttpStatus.CONFLICT,issues.getFirst().code(),"Quyền lợi chưa đủ điều kiện sử dụng.",Map.of("grantId",grantId,"blockers",issues));
        return grant;
    }
    private boolean matches(BookingCruisePackageBenefitSnapshot grant,ResourceType type,UUID id) {
        if(type==ResourceType.SERVICE&&Objects.equals(grant.getServiceId(),id))return true;
        return grant.getOccurrenceBindings()!=null&&grant.getOccurrenceBindings().stream()
                .anyMatch(o->type.name().equals(o.get("resourceType"))&&id.toString().equals(o.get("resourceId")));
    }
    private List<Blocker> blockers(BookingCruisePackageBenefitSnapshot grant) {
        var result=new ArrayList<Blocker>();
        boolean confirmed=Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "BookingFareSnapshot" f JOIN "BookingPassenger" p USING(booking_passenger_id)
                JOIN "Booking" b ON b.booking_id=f.booking_id AND b.booking_id=p.booking_id
                WHERE f.booking_passenger_id=? AND b.booking_status='Confirmed' AND p.participation_status='Active')
                """,Boolean.class,grant.getBookingPassengerId()));
        if(!confirmed)result.add(new Blocker("ENTITLEMENT_NOT_ACTIVE","Đặt chỗ chưa xác nhận hoặc hành khách/đặt chỗ không còn hiệu lực.",grant.getId()));
        if(grant.getEntitlementGrantedAt()==null)result.add(new Blocker("ENTITLEMENT_NOT_FOUND","Chưa có grant được cấp từ xác nhận mua thực tế.",grant.getId()));
        result.addAll(policy.blockers(grant));
        return List.copyOf(result);
    }
    private long used(BookingCruisePackageBenefitSnapshot grant) {
        long participations=Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM \"Participation\" p JOIN \"Registration\" r USING(registration_id) WHERE r.fare_benefit_snapshot_id=? AND p.status='COMPLETED'",Long.class,grant.getId()));
        long services=Objects.requireNonNull(jdbc.queryForObject("SELECT COALESCE(sum(quantity),0) FROM \"ServiceUsage\" WHERE fare_benefit_snapshot_id=?",Long.class,grant.getId()));
        return grant.getServiceId()==null?participations:services;
    }
    private void requireQuantity(BookingCruisePackageBenefitSnapshot grant,int quantity) {
        if(quantity<1)throw business("ENTITLEMENT_QUANTITY_INVALID","Số lượng fulfillment phải dương.");
        if("LIMITED_INCLUDED".equals(grant.getInclusion())&&(grant.getQuantityLimit()==null||quantity>(long)grant.getQuantityLimit()-used(grant)))
            throw business("ENTITLEMENT_EXHAUSTED","Số lượng quyền lợi còn lại không đủ; hệ thống không tiêu thụ thêm lượt.");
    }
    private void requireOperationalTime(BookingCruisePackageBenefitSnapshot grant,ResourceType type,UUID id,boolean fulfillment,Instant occurredAt) {
        Instant now=Instant.now(),at=occurredAt==null?now:occurredAt;
        String sql=switch(type) {
            case ACTIVITY -> "SELECT cruise_id,start_time AS starts_at,end_time AS ends_at,status FROM \"OnboardActivity\" WHERE onboard_activity_id=?";
            case EXCURSION -> "SELECT i.cruise_id,e.start_time AS starts_at,e.planned_return_time AS ends_at,e.status FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE e.shore_excursion_id=?";
            case SERVICE -> null;
        };
        if(sql==null) {
            if(fulfillment&&grant.getUsageStartsAt()!=null&&(at.isBefore(grant.getUsageStartsAt())||!at.isBefore(grant.getUsageEndsAt())))
                throw business("BENEFIT_USAGE_WINDOW_INVALID","Thời điểm dùng dịch vụ nằm ngoài khoảng sử dụng đã mua.");
            return; // ServiceSchedule is validated by the existing ServiceRequest lock/transition path.
        }
        var rows=jdbc.queryForList(sql,id);
        if(rows.isEmpty())throw business("ENTITLEMENT_RESOURCE_MISMATCH","Lần phục vụ trong snapshot không còn tồn tại.");
        var row=rows.getFirst();
        UUID cruiseId=jdbc.queryForObject("SELECT b.cruise_id FROM \"Booking\" b JOIN \"BookingPassenger\" p USING(booking_id) WHERE p.booking_passenger_id=?",UUID.class,grant.getBookingPassengerId());
        if(!Objects.equals(cruiseId,row.get("cruise_id")))throw business("ENTITLEMENT_RESOURCE_MISMATCH","Lần phục vụ không thuộc chuyến khởi hành đã mua.");
        Instant start=((java.sql.Timestamp)row.get("starts_at")).toInstant(),end=((java.sql.Timestamp)row.get("ends_at")).toInstant();
        if(grant.getUsageStartsAt()!=null&&(start.isBefore(grant.getUsageStartsAt())||end.isAfter(grant.getUsageEndsAt())))
            throw business("BENEFIT_USAGE_WINDOW_INVALID","Session nằm ngoài khoảng sử dụng trong snapshot đã mua.");
        if(fulfillment) {
            if(!Set.of("IN_PROGRESS","COMPLETED").contains(row.get("status").toString()))
                throw business("BENEFIT_CAPACITY_UNAVAILABLE","Hoạt động/tour không còn ở trạng thái vận hành cho phép ghi nhận fulfillment.");
            if(occurredAt==null||at.isBefore(start)||at.isAfter(end))throw business("BENEFIT_USAGE_WINDOW_INVALID","Thiếu hoặc sai thời điểm tham gia thực tế trong lịch session.");
        } else if(!end.isAfter(now)||!Set.of("OPEN","FULL").contains(row.get("status").toString()))
            throw business("BENEFIT_CAPACITY_UNAVAILABLE","Lần phục vụ không còn mở đăng ký.");
    }
    private EntitlementView view(BookingCruisePackageBenefitSnapshot grant) {
        var issues=new ArrayList<>(blockers(grant));
        if("OPTIONAL".equals(grant.getInclusion()))issues.add(new Blocker("ADDON_PURCHASE_REQUIRED","Cần giao dịch mua thêm đã xác nhận.",grant.getId()));
        if("NOT_INCLUDED".equals(grant.getInclusion()))issues.add(new Blocker("BENEFIT_NOT_INCLUDED","Không phải quyền lợi miễn phí.",grant.getId()));
        Long used=policy.blockers(grant).isEmpty()?used(grant):null;
        Long granted="LIMITED_INCLUDED".equals(grant.getInclusion())&&grant.getQuantityLimit()!=null?grant.getQuantityLimit().longValue():null;
        Long remaining=granted==null||used==null?null:Math.max(0L,granted-used);
        if(remaining!=null&&remaining==0)issues.add(new Blocker("ENTITLEMENT_EXHAUSTED","Quyền lợi đã dùng hết số lượng được cấp.",grant.getId()));
        if(grant.getUsageEndsAt()!=null&&!grant.getUsageEndsAt().isAfter(Instant.now()))
            issues.add(new Blocker("BENEFIT_USAGE_WINDOW_INVALID","Khoảng sử dụng đã kết thúc; không đăng ký lần phục vụ mới.",grant.getId()));
        String packageName=jdbc.queryForObject("SELECT template_name FROM \"BookingFareSnapshot\" WHERE booking_passenger_id=?",String.class,grant.getBookingPassengerId());
        var registrations=new ArrayList<Map<String,Object>>(jdbc.queryForList("SELECT registration_id AS operation_id,'REGISTRATION' AS operation_type,status::text AS status,quantity FROM \"Registration\" WHERE fare_benefit_snapshot_id=? ORDER BY registration_time,registration_id",grant.getId()));
        registrations.addAll(jdbc.queryForList("SELECT service_request_id AS operation_id,'SERVICE_REQUEST' AS operation_type,status::text AS status,quantity FROM \"ServiceRequest\" WHERE fare_benefit_snapshot_id=? ORDER BY request_time,service_request_id",grant.getId()));
        return new EntitlementView(grant.getId(),grant.getBookingPassengerId(),grant.getResourceName(),grant.getInclusion(),packageName,
                grant.getEntitlementGrantedAt(),issues.stream().anyMatch(b->Set.of("ENTITLEMENT_NOT_ACTIVE","ENTITLEMENT_NOT_FOUND").contains(b.code()))?"INACTIVE":"ACTIVE",
                grant.getUsagePolicy(),grant.getUsageUnit(),grant.getServiceUnitCode(),granted,used,remaining,grant.getRequiresReservation(),
                grant.getUsageStartsAt(),grant.getUsageEndsAt(),grant.getOccurrenceBindings(),List.copyOf(registrations),List.copyOf(issues),issues.isEmpty());
    }
    private BusinessException business(String code,String message){return new BusinessException(HttpStatus.CONFLICT,code,message);}
}
