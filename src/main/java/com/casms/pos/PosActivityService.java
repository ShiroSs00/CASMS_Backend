package com.casms.pos;

import com.casms.activity.controller.ActivityController.*;
import com.casms.activity.entity.*;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.service.ActivityService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.casms.pos.PosPermission.ResourceType.ACTIVITY;

/** Source -> area -> terminal restrictions, always within the employee's selected cruise. */
@Service @RequiredArgsConstructor @Transactional
public class PosActivityService {
    private final JdbcTemplate jdbc;
    private final PosRepository repository;
    private final PosService sessions;
    private final ResourceOperationService policies;
    private final ActivityService activities;
    private final OnboardActivityRepository activityRepository;

    public List<PosDtos.AreaActivity> assignments(UUID ship,UUID area) {
        area(ship,area,false,false);
        return jdbc.query("SELECT a.*,COALESCE(s.permissions,'{}') AS source_permissions FROM \"AreaActivityAssignment\" a "
                +"LEFT JOIN \"ActivityOperationPolicy\" s ON s.activity_id=a.activity_id WHERE a.area_object_id=? ORDER BY a.activity_id",(r,n)->{
                    var effective=PosRepository.permissions(r,"source_permissions");boolean inherit=r.getBoolean("inherit_permissions");
                    if(!inherit)effective.retainAll(PosRepository.permissions(r,"permissions"));
                    return new PosDtos.AreaActivity(area,PosRepository.uuid(r,"activity_id"),Set.copyOf(effective),r.getBoolean("enabled"),inherit);
                },area);
    }
    public PosDtos.AreaActivity assign(UUID ship,UUID area,UUID activity,PosDtos.AssignmentInput input) {
        var physical=area(ship,area,true,input.enabled());
        var a=activityRepository.lockActivity(activity).orElseThrow(()->new ResourceNotFoundException("Activity",activity));
        if(!matchesVenue(a,physical))throw new BusinessException("ACTIVITY_AREA_MISMATCH","Activity must belong to this ship and, when specified, this area's deck");
        ResourceOperationService.validate(ACTIVITY,input.permissions(),policies.get(ACTIVITY,activity).permissions());
        jdbc.update("INSERT INTO \"AreaActivityAssignment\" (area_object_id,activity_id,permissions,inherit_permissions,enabled) VALUES (?,?,string_to_array(?,','),?,?) "
                +"ON CONFLICT (area_object_id,activity_id) DO UPDATE SET permissions=excluded.permissions,inherit_permissions=excluded.inherit_permissions,enabled=excluded.enabled",
                area,activity,PosRepository.csv(input.permissions()),input.permissions()==null,input.enabled());
        return assignments(ship,area).stream().filter(p->p.activityId().equals(activity)).findFirst().orElseThrow();
    }
    public void unassign(UUID ship,UUID area,UUID activity) {
        area(ship,area,true,false);jdbc.update("DELETE FROM \"AreaActivityAssignment\" WHERE area_object_id=? AND activity_id=?",area,activity);
    }
    public List<PosDtos.TerminalActivity> terminalPolicies(UUID terminal,UUID area) {
        var d=device(terminal);area(d.shipId(),area,true,false);var sources=assignments(d.shipId(),area);
        return jdbc.query("SELECT a.activity_id,p.permissions,(p.activity_id IS NULL OR p.inherit_permissions) AS inherited, "
                +"COALESCE(p.enabled,true) AS enabled FROM \"AreaActivityAssignment\" a LEFT JOIN \"PosAreaActivityPermission\" p "
                +"ON p.area_object_id=a.area_object_id AND p.activity_id=a.activity_id AND p.terminal_id=? WHERE a.area_object_id=? ORDER BY a.activity_id",(r,n)->{
                    UUID id=PosRepository.uuid(r,"activity_id");boolean inherit=r.getBoolean("inherited");var effective=EnumSet.noneOf(PosPermission.class);
                    sources.stream().filter(s->s.activityId().equals(id)).findFirst().ifPresent(s->effective.addAll(s.permissions()));
                    effective.retainAll(d.permissions());if(!inherit)effective.retainAll(PosRepository.permissions(r,"permissions"));
                    return new PosDtos.TerminalActivity(terminal,area,id,Set.copyOf(effective),r.getBoolean("enabled"),inherit);
                },terminal,area);
    }
    public PosDtos.TerminalActivity terminalPolicy(UUID terminal,UUID area,UUID activity,PosDtos.AssignmentInput input) {
        var d=device(terminal);area(d.shipId(),area,true,input.enabled());
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"PosTerminalArea\" WHERE terminal_id=? AND area_object_id=? AND enabled)",Boolean.class,terminal,area)))
            throw new AccessDeniedException("Area is not enabled on this POS terminal");
        var source=assignments(d.shipId(),area).stream().filter(p->p.activityId().equals(activity)).findFirst()
                .orElseThrow(()->new BusinessException("POS_AREA_ACTIVITY_REQUIRED","Assign the activity to the area first"));
        ResourceOperationService.validate(ACTIVITY,input.permissions(),source.permissions());
        if(input.permissions()!=null && !d.permissions().containsAll(input.permissions()))
            throw new BusinessException("POS_PERMISSION_OUTSIDE_PARENT_SCOPE","Operations must be within terminal permissions");
        jdbc.update("INSERT INTO \"PosAreaActivityPermission\" (terminal_id,area_object_id,activity_id,permissions,inherit_permissions,enabled) VALUES (?,?,?,string_to_array(?,','),?,?) "
                +"ON CONFLICT (terminal_id,area_object_id,activity_id) DO UPDATE SET permissions=excluded.permissions,inherit_permissions=excluded.inherit_permissions,enabled=excluded.enabled",
                terminal,area,activity,PosRepository.csv(input.permissions()),input.permissions()==null,input.enabled());
        return terminalPolicies(terminal,area).stream().filter(p->p.activityId().equals(activity)).findFirst().orElseThrow();
    }
    public List<PosDtos.AvailableActivity> available(String token,CasmsPrincipal employee) {
        return available(sessions.access(token,employee)).stream().filter(a->a.permissions().contains(PosPermission.ACTIVITY_READ)).toList();
    }
    private List<PosDtos.AvailableActivity> available(PosService.Access access) {
        return jdbc.query("SELECT a.activity_id,sp.permissions AS source_permissions,a.permissions AS area_permissions,a.inherit_permissions AS area_inherit, "
                +"p.permissions AS pos_permissions,(p.activity_id IS NULL OR p.inherit_permissions) AS pos_inherit "
                +"FROM \"AreaActivityAssignment\" a JOIN \"ActivityOperationPolicy\" sp ON sp.activity_id=a.activity_id "
                +"JOIN \"OnboardActivity\" act ON act.onboard_activity_id=a.activity_id "
                +"LEFT JOIN \"PosAreaActivityPermission\" p ON p.area_object_id=a.area_object_id AND p.activity_id=a.activity_id AND p.terminal_id=? "
                +"WHERE a.area_object_id=? AND a.enabled AND (p.activity_id IS NULL OR p.enabled) AND act.cruise_id=? ORDER BY act.activity_code",(r,n)->{
                    var effective=PosRepository.permissions(r,"source_permissions");effective.retainAll(access.terminal().permissions());
                    if(!r.getBoolean("area_inherit"))effective.retainAll(PosRepository.permissions(r,"area_permissions"));
                    if(!r.getBoolean("pos_inherit"))effective.retainAll(PosRepository.permissions(r,"pos_permissions"));
                    var a=activities.activity(PosRepository.uuid(r,"activity_id"));if(!matchesVenue(a,access.area()))effective.clear();
                    return new PosDtos.AvailableActivity(ActivityView.of(a),Set.copyOf(effective));
                },access.terminal().terminalId(),access.area().id(),access.assignment().cruiseId()).stream().filter(a->!a.permissions().isEmpty()).toList();
    }
    private void require(PosService.Access access,UUID id,PosPermission permission) {
        if(available(access).stream().noneMatch(a->a.activity().id().equals(id) && a.permissions().contains(permission)))
            throw new AccessDeniedException("Activity operation is outside this POS area, cruise or current source permissions");
    }
    public ActivityView lifecycle(String token,CasmsPrincipal employee,UUID id,String action) {
        var access=sessions.access(token,employee);
        activityRepository.lockActivity(id).orElseThrow(()->new ResourceNotFoundException("Activity",id));
        require(access,id,PosPermission.ACTIVITY_LIFECYCLE);return ActivityView.of(activities.lifecycle(id,action));
    }
    public List<RegistrationView> registrations(String token,CasmsPrincipal employee,UUID id) {
        var access=sessions.access(token,employee);
        if(available(access).stream().noneMatch(a->a.activity().id().equals(id) && (a.permissions().contains(PosPermission.ACTIVITY_REGISTRATION)||a.permissions().contains(PosPermission.ACTIVITY_ATTENDANCE))))
            throw new AccessDeniedException("Registration read is outside this POS scope");
        return activities.listRegistrations(id,employee.getUserId(),true).stream().map(RegistrationView::of).toList();
    }
    public RegistrationView register(String token,CasmsPrincipal employee,UUID id,RegistrationRequest input) {
        var access=sessions.access(token,employee);
        activityRepository.lockActivity(id).orElseThrow(()->new ResourceNotFoundException("Activity",id));
        require(access,id,PosPermission.ACTIVITY_REGISTRATION);
        return RegistrationView.of(activities.register(input.bookingPassengerId(),id,input.quantity(),employee.getUserId(),true));
    }
    public RegistrationView changeRegistration(String token,CasmsPrincipal employee,UUID id,String action,RegistrationUpdateRequest update,String reason) {
        var access=sessions.access(token,employee);var r=activities.registration(id);requireRegistration(access,r,PosPermission.ACTIVITY_REGISTRATION);
        return RegistrationView.of(switch(action) {
            case "confirm"->activities.confirmRegistration(id,employee.getUserId(),true);
            case "cancel"->activities.cancelRegistration(id,reason,employee.getUserId(),true);
            case "update"->{if(update==null)throw new BusinessException("INVALID_POS_ACTIVITY_ACTION","Use PUT with a registration update body");
                yield activities.updatePending(id,update.quantity(),update.notes(),employee.getUserId(),true);}
            default->throw new BusinessException("INVALID_POS_ACTIVITY_ACTION","Unsupported registration action");
        });
    }
    public List<ParticipationView> participations(String token,CasmsPrincipal employee,UUID activity) {
        var access=sessions.access(token,employee);require(access,activity,PosPermission.ACTIVITY_ATTENDANCE);
        return jdbc.queryForList("SELECT p.participation_id FROM \"Participation\" p JOIN \"Registration\" r ON r.registration_id=p.registration_id WHERE r.onboard_activity_id=? ORDER BY p.participation_id",UUID.class,activity)
                .stream().map(activities::participation).map(ParticipationView::of).toList();
    }
    public ParticipationView attendance(String token,CasmsPrincipal employee,UUID id,String action) {
        var access=sessions.access(token,employee);var p=activities.participation(id);requireRegistration(access,p.getRegistration(),PosPermission.ACTIVITY_ATTENDANCE);
        return ParticipationView.of(activities.attendance(id,action));
    }
    private void requireRegistration(PosService.Access access,Registration r,PosPermission permission) {
        if(r.getOnboardActivity()==null || r.getShoreExcursion()!=null || !access.assignment().cruiseId().equals(r.getBookingPassenger().getBooking().getCruise().getCruiseId()))
            throw new AccessDeniedException("Registration is outside this POS cruise or is not an onboard activity");
        require(access,r.getOnboardActivity().getOnboardActivityId(),permission);
    }
    private PosDtos.Terminal device(UUID id) {return repository.device(id,true).orElseThrow(()->new ResourceNotFoundException("POS terminal",id)).terminal();}
    private PosRepository.Area area(UUID ship,UUID id,boolean lock,boolean ready) {
        var a=repository.area(ship,id,lock).orElseThrow(()->new ResourceNotFoundException("Physical area in ship",id));
        if(ready && !"READY".equals(a.modelStatus()))throw new BusinessException("POS_AREA_NOT_PUBLISHED","POS requires an area in a READY model");return a;
    }
    private static boolean matchesVenue(OnboardActivity a,PosRepository.Area area) {
        return a.getCruise()!=null && a.getCruise().getShip()!=null && area.shipId().equals(a.getCruise().getShip().getShipId())
                && (a.getDeck()==null || area.deckId().equals(a.getDeck().getDeckId()))
                && (a.getCabin()==null || a.getCabin().getDeck()!=null && area.deckId().equals(a.getCabin().getDeck().getDeckId()));
    }
}
