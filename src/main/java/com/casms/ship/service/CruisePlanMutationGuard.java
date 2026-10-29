package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

/** All existing writers use the same Cruise lock as plan submission; UI locks are not an authorization boundary. */
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class CruisePlanMutationGuard {
    private final CruiseMainflowService plans;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final jakarta.persistence.EntityManager em;
    private static final Map<String,Set<String>> WRITERS=Map.ofEntries(
        Map.entry("CruiseService",Set.of("updateDraft","deleteDraft","assignShip","changeAssignedShip","unassignShip","applyChange")),
        Map.entry("ItineraryService",Set.of("create","updateDraft","deleteDraft","applyChange")),
        Map.entry("ItineraryDayService",Set.of("create","update","deleteUnused","setDayType","setShipTimeZone","setDate")),
        Map.entry("CruiseStopService",Set.of("add","update","removeDraft","reorder","reorderWithReason","setAllAboardTime","setPlannedArrival","setPlannedDeparture","omit","substitute")),
        Map.entry("ActivityService",Set.of("createActivity","updateActivity","deleteActivity")),
        Map.entry("ShoreExcursionService",Set.of("create","update","updateCommercial","deleteDraft")),
        Map.entry("CruisePricingPolicyService",Set.of("savePricingPolicy")),
        Map.entry("CruisePackageService",Set.of("assignPackage","removePackage")),
        Map.entry("OperationalProgramMappingService",Set.of("syncProgramsForCruise")),
        Map.entry("ServiceScheduleManagementService",Set.of("create")),
        Map.entry("ServicepackageService",Set.of("savePackage","addPackageService","updatePackageService","removePackageService","addPackageActivity","updatePackageActivity","removePackageActivity","addPackageExcursion","updatePackageExcursion","removePackageExcursion"))
    );
    @Around("execution(public * com.casms.ship.service.*.*(..)) || execution(public * com.casms.activity.service.*.*(..)) || execution(public * com.casms.servicepackage.service.ServicepackageService.*(..)) || execution(public * com.casms.servicepackage.service.ServiceScheduleManagementService.*(..))")
    public Object guard(ProceedingJoinPoint call) throws Throwable {
        String type=call.getTarget().getClass().getSimpleName(),method=call.getSignature().getName();
        boolean operational=type.equals("CruiseStaffService")&&Set.of("assignStaff","removeStaff").contains(method)
                ||type.equals("CruisePreparationService")&&Set.of("submitSection","confirmSection","returnSection","invalidateSignoffs").contains(method);
        if(!operational&&!WRITERS.getOrDefault(type,Set.of()).contains(method))return call.proceed();
        try {
            return new TransactionTemplate(transactions).execute(tx -> {
                List<UUID> ids=operational?List.of((UUID)call.getArgs()[0]):resolve(type,method,call.getArgs());
                for(UUID id:ids) {
                    if(operational)plans.lockOperational(id);else plans.assertEditable(id);
                    if(!operational && structural(type,method,call.getArgs()) && "FIXED".equals(jdbc.queryForObject("SELECT itinerary_mode FROM \"Cruise\" WHERE cruise_id=?",String.class,id)))
                        throw new BusinessException(HttpStatus.CONFLICT,"FIXED_ITINERARY_READ_ONLY","Fixed cruise itinerary cannot be structurally edited");
                }
                try{
                    Object result=call.proceed();
                    if(operational){
                        em.flush();
                        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        if(auth!=null&&auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal)
                            plans.recordOperationalEvent(ids.getFirst(),method.toUpperCase(java.util.Locale.ROOT),principal.getUserId());
                    }
                    return result;
                }catch(RuntimeException|Error ex){throw ex;}catch(Throwable ex){throw new Wrapped(ex);}
            });
        }catch(Wrapped ex){throw ex.getCause();}
    }
    private static class Wrapped extends RuntimeException {Wrapped(Throwable ex){super(ex);}}
    private Object field(Object bean,String property) {
        if(bean==null)return null;
        if(bean.getClass().isRecord())try{return bean.getClass().getMethod(property).invoke(bean);}catch(ReflectiveOperationException ex){throw new IllegalStateException("Unmapped record property",ex);}
        return new BeanWrapperImpl(bean).getPropertyValue(property);
    }
    private UUID lookup(String sql,Object id) {
        List<UUID> ids=jdbc.queryForList(sql,UUID.class,id);
        if(ids.isEmpty())throw new com.casms.common.exception.ResourceNotFoundException("Cruise scope",id);
        return ids.getFirst();
    }
    private List<UUID> resolve(String type,String method,Object[] args) {
        UUID id;
        switch(type) {
            case "CruiseService","CruisePricingPolicyService","CruisePackageService","OperationalProgramMappingService" -> id=(UUID)args[0];
            case "ItineraryService" -> id=method.equals("create")?(UUID)field(args[0],"cruiseId"):lookup("SELECT cruise_id FROM \"Itinerary\" WHERE itinerary_id=?",args[0]);
            case "ItineraryDayService" -> id=method.equals("create")?lookup("SELECT cruise_id FROM \"Itinerary\" WHERE itinerary_id=?",field(args[0],"itineraryId")):lookup("SELECT i.cruise_id FROM \"ItineraryDay\" d JOIN \"Itinerary\" i USING(itinerary_id) WHERE itinerary_day_id=?",args[0]);
            case "CruiseStopService" -> id=method.equals("add")?lookup("SELECT cruise_id FROM \"Itinerary\" WHERE itinerary_id=?",field(args[0],"itineraryId")):lookup("SELECT i.cruise_id FROM \"CruiseStop\" s JOIN \"Itinerary\" i USING(itinerary_id) WHERE cruise_stop_id=?",args[0]);
            case "ActivityService" -> id=method.equals("createActivity")?(UUID)args[1]:lookup("SELECT cruise_id FROM \"OnboardActivity\" WHERE onboard_activity_id=?",args[0]);
            case "ServiceScheduleManagementService" -> {
                if(args[1]==null&&args[2]==null)return List.of();
                id=args[1]!=null?(UUID)args[1]:lookup("SELECT i.cruise_id FROM \"CruiseStop\" s JOIN \"Itinerary\" i USING(itinerary_id) WHERE cruise_stop_id=?",args[2]);
            }
            case "ShoreExcursionService" -> id=method.equals("create")?lookup("SELECT i.cruise_id FROM \"CruiseStop\" s JOIN \"Itinerary\" i USING(itinerary_id) WHERE cruise_stop_id=?",field(args[0],"cruiseStopId")):lookup("SELECT i.cruise_id FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE shore_excursion_id=?",args[0]);
            case "ServicepackageService" -> {
                Object packageId=method.equals("savePackage")?field(args[0],"tourPackageId"):args[0];
                return packageId==null?List.of():jdbc.queryForList("SELECT cruise_id FROM \"CruiseTourPackage\" WHERE tour_package_id=? ORDER BY cruise_id",UUID.class,packageId);
            }
            default -> throw new IllegalStateException("Unmapped material writer");
        }
        if(id==null)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"CRUISE_SCOPE_REQUIRED","Cruise scope required");
        return List.of(id);
    }
    private boolean structural(String type,String method,Object[] args) {
        if(type.equals("ShoreExcursionService")&&method.equals("updateCommercial"))return false;
        if(Set.of("ItineraryService","ItineraryDayService","CruiseStopService").contains(type))return true;
        if(type.equals("ActivityService")&&method.equals("updateActivity")) {
            Map<String,Object> old=jdbc.queryForMap("SELECT start_time,end_time,location,activity_name,description,deck_id,cabin_id FROM \"OnboardActivity\" WHERE onboard_activity_id=?",args[0]);
            Object deck=args.length>2?args[2]:field(args[1],"deck");
            Object cabin=args.length>3?args[3]:field(args[1],"cabin");
            if(args.length==2){deck=deck==null?null:field(deck,"deckId");cabin=cabin==null?null:field(cabin,"cabinId");}
            if(!Objects.equals(old.get("deck_id"),deck)||!Objects.equals(old.get("cabin_id"),cabin))return true;
            return !sameTime(old.get("start_time"),field(args[1],"startTime"))||!sameTime(old.get("end_time"),field(args[1],"endTime"))||!Objects.equals(old.get("location"),field(args[1],"location"))||!Objects.equals(old.get("activity_name"),field(args[1],"activityName"))||!Objects.equals(old.get("description"),field(args[1],"description"));
        }
        if(type.equals("ShoreExcursionService")&&method.equals("update")) {
            Map<String,Object> old=jdbc.queryForMap("SELECT start_time,planned_return_time,cruise_stop_id FROM \"ShoreExcursion\" WHERE shore_excursion_id=?",args[0]);
            return !sameTime(old.get("start_time"),field(args[1],"startTime"))||!sameTime(old.get("planned_return_time"),field(args[1],"plannedReturnTime"))||!Objects.equals(old.get("cruise_stop_id"),field(args[1],"cruiseStopId"));
        }
        return type.equals("ActivityService")||type.equals("ShoreExcursionService");
    }
    private boolean sameTime(Object db,Object input) {return Objects.equals(db instanceof java.sql.Timestamp t?t.toInstant():db instanceof java.time.OffsetDateTime t?t.toInstant():db,input instanceof Instant?input:null);}
}
