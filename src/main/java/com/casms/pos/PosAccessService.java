package com.casms.pos;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.enums.Permission;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Employee assignments gate access; operation capabilities remain scoped to device, area and service. */
@Service @RequiredArgsConstructor @Transactional
public class PosAccessService {
    private final JdbcTemplate jdbc;
    private final PosRepository repository;
    private final UserRepository users;
    private final RolePermissionRepository roles;
    private static final String STAFF_SELECT="SELECT a.*,cs.user_id,cs.cruise_id FROM \"AreaStaffAssignment\" a "
            +"JOIN \"CruiseStaff\" cs ON cs.cruise_staff_id=a.cruise_staff_id JOIN \"Cruise\" c ON c.cruise_id=cs.cruise_id ";
    private static final org.springframework.jdbc.core.RowMapper<PosDtos.AreaStaff> STAFF=(r,n)->new PosDtos.AreaStaff(
            PosRepository.uuid(r,"assignment_id"),PosRepository.uuid(r,"area_object_id"),PosRepository.uuid(r,"user_id"),
            PosRepository.uuid(r,"cruise_staff_id"),PosRepository.uuid(r,"cruise_id"),r.getBoolean("enabled"),
            PosRepository.instant(r,"starts_at"),PosRepository.instant(r,"ends_at"));
    private static final String QUALIFIED=" AND a.enabled AND a.starts_at<=clock_timestamp() AND (a.ends_at IS NULL OR a.ends_at>clock_timestamp()) "
            +"AND cs.status='ACTIVE' AND c.ship_id=? AND c.status IN ('PUBLISHED','IN_PROGRESS','COMPLETED')";

    public List<PosDtos.AreaStaff> staff(UUID ship,UUID area) {
        area(ship,area,false,false);return jdbc.query(STAFF_SELECT+"WHERE a.area_object_id=? ORDER BY cs.cruise_id,cs.user_id",STAFF,area);
    }
    public PosDtos.AreaStaff assignStaff(UUID ship,UUID area,UUID userId,PosDtos.AreaStaffInput input,CasmsPrincipal actor) {
        area(ship,area,true,input.enabled());
        var user=users.findById(userId).orElseThrow(()->new ResourceNotFoundException("Employee",userId));
        var principal=new CasmsPrincipal(user,roles);
        if(input.enabled() && (!principal.isStaff() || !principal.isEnabled() || !principal.isAccountNonLocked() || !principal.hasPermission(Permission.POS_ACCESS)))
            throw invalid("POS_STAFF_NOT_ELIGIBLE","Assign an active employee with POS_ACCESS");
        var crew=jdbc.queryForList("SELECT cs.cruise_staff_id FROM \"CruiseStaff\" cs JOIN \"Cruise\" c ON c.cruise_id=cs.cruise_id "
                +"WHERE cs.user_id=? AND cs.cruise_id=? AND cs.status='ACTIVE' AND c.ship_id=? "
                +"AND c.status IN ('DRAFT','PUBLISHED','IN_PROGRESS') ORDER BY cs.assigned_at DESC FOR SHARE OF cs",UUID.class,userId,input.cruiseId(),ship);
        if(crew.isEmpty())throw invalid("POS_CRUISE_STAFF_REQUIRED","Assign the employee to an eligible cruise on this ship first");
        Instant start=input.startsAt()==null?Instant.now():input.startsAt(),end=input.endsAt();
        if(end!=null && !end.isAfter(start))throw invalid("POS_INVALID_ASSIGNMENT_WINDOW","Assignment end must be after its start");
        UUID crewId=crew.getFirst();
        jdbc.update("INSERT INTO \"AreaStaffAssignment\" (assignment_id,area_object_id,cruise_staff_id,enabled,starts_at,ends_at,assigned_by_user_id,assigned_at,updated_at) "
                +"VALUES (?,?,?,?,?,?,?,now(),now()) ON CONFLICT (area_object_id,cruise_staff_id) DO UPDATE SET enabled=excluded.enabled,"
                +"starts_at=excluded.starts_at,ends_at=excluded.ends_at,assigned_by_user_id=excluded.assigned_by_user_id,updated_at=now()",
                UUID.randomUUID(),area,crewId,input.enabled(),Timestamp.from(start),end==null?null:Timestamp.from(end),actor.getUserId());
        var result=jdbc.query(STAFF_SELECT+"WHERE a.area_object_id=? AND a.cruise_staff_id=?",STAFF,area,crewId).getFirst();
        revokeAssignment(result.assignmentId());return result;
    }
    public void unassignStaff(UUID ship,UUID area,UUID userId,UUID cruiseId) {
        area(ship,area,true,false);
        var ids=jdbc.queryForList("SELECT a.assignment_id FROM \"AreaStaffAssignment\" a JOIN \"CruiseStaff\" cs ON cs.cruise_staff_id=a.cruise_staff_id "
                +"WHERE a.area_object_id=? AND cs.user_id=? AND cs.cruise_id=?",UUID.class,area,userId,cruiseId);
        for(UUID id:ids) {
            jdbc.update("UPDATE \"AreaStaffAssignment\" SET enabled=false,updated_at=now() WHERE assignment_id=?",id);revokeAssignment(id);
        }
    }
    private void revokeAssignment(UUID id) {
        jdbc.update("UPDATE \"PosSession\" SET revoked_at=now() WHERE area_staff_assignment_id=? AND revoked_at IS NULL",id);
    }
    public List<PosDtos.TerminalArea> areas(UUID terminal) {
        device(terminal);return jdbc.query("SELECT * FROM \"PosTerminalArea\" WHERE terminal_id=? ORDER BY area_object_id",
                (r,n)->new PosDtos.TerminalArea(terminal,PosRepository.uuid(r,"area_object_id"),r.getBoolean("enabled")),terminal);
    }
    public PosDtos.TerminalArea linkArea(UUID terminal,UUID area,boolean enabled) {
        var d=device(terminal);area(d.shipId(),area,true,enabled);
        jdbc.update("INSERT INTO \"PosTerminalArea\" (terminal_id,area_object_id,enabled) VALUES (?,?,?) "
                +"ON CONFLICT (terminal_id,area_object_id) DO UPDATE SET enabled=excluded.enabled",terminal,area,enabled);
        jdbc.update("UPDATE \"PosSession\" SET revoked_at=now() WHERE terminal_id=? AND revoked_at IS NULL "
                +"AND area_staff_assignment_id IN (SELECT assignment_id FROM \"AreaStaffAssignment\" WHERE area_object_id=?)",terminal,area);
        return new PosDtos.TerminalArea(terminal,area,enabled);
    }
    public List<PosDtos.TerminalService> policies(UUID terminal,UUID area) {
        var d=device(terminal);area(d.shipId(),area,true,false);
        return jdbc.query("SELECT * FROM \"PosAreaServicePermission\" WHERE terminal_id=? AND area_object_id=? ORDER BY service_id",
                (r,n)->new PosDtos.TerminalService(terminal,area,PosRepository.uuid(r,"service_id"),PosRepository.permissions(r,"permissions"),r.getBoolean("enabled")),terminal,area);
    }
    public PosDtos.TerminalService policy(UUID terminal,UUID area,UUID service,PosDtos.AssignmentInput input) {
        var d=device(terminal);area(d.shipId(),area,true,input.enabled());requireAreaLink(d,area);
        var source=repository.assignments(area).stream().filter(a->a.serviceId().equals(service)).findFirst()
                .orElseThrow(()->invalid("POS_AREA_SERVICE_REQUIRED","Assign this service to the selected area first"));
        if(!source.permissions().containsAll(input.permissions()) || !d.permissions().containsAll(input.permissions()))
            throw invalid("POS_PERMISSION_OUTSIDE_PARENT_SCOPE","POS service permissions must be within area and terminal permissions");
        jdbc.update("INSERT INTO \"PosAreaServicePermission\" (terminal_id,area_object_id,service_id,permissions,enabled) VALUES (?,?,?,string_to_array(?,','),?) "
                +"ON CONFLICT (terminal_id,area_object_id,service_id) DO UPDATE SET permissions=excluded.permissions,enabled=excluded.enabled",
                terminal,area,service,PosRepository.csv(input.permissions()),input.enabled());
        return new PosDtos.TerminalService(terminal,area,service,input.permissions(),input.enabled());
    }
    public void removePolicy(UUID terminal,UUID area,UUID service) {
        var d=device(terminal);area(d.shipId(),area,true,false);
        jdbc.update("DELETE FROM \"PosAreaServicePermission\" WHERE terminal_id=? AND area_object_id=? AND service_id=?",terminal,area,service);
    }
    public PosDtos.AreaStaff login(PosDtos.Terminal terminal,PosDtos.LoginInput input,UUID user) {
        UUID areaId=input.areaId()==null?terminal.areaId():input.areaId();
        area(terminal.shipId(),areaId,true,true);requireAreaLink(terminal,areaId);
        List<PosDtos.AreaStaff> candidates;
        if(input.cruiseId()==null)candidates=jdbc.query(STAFF_SELECT+"WHERE a.area_object_id=? AND cs.user_id=?"+QUALIFIED,STAFF,areaId,user,terminal.shipId());
        else candidates=jdbc.query(STAFF_SELECT+"WHERE a.area_object_id=? AND cs.user_id=? AND cs.cruise_id=?"+QUALIFIED,STAFF,areaId,user,input.cruiseId(),terminal.shipId());
        if(candidates.isEmpty())throw new AccessDeniedException("No active employee assignment for this POS area and cruise");
        if(candidates.size()!=1)throw new BusinessException(HttpStatus.CONFLICT,"POS_CRUISE_SELECTION_REQUIRED","Select exactly one assigned cruise for this POS session");
        return candidates.getFirst();
    }
    public PosDtos.AreaStaff sessionAssignment(UUID assignment,PosDtos.Terminal terminal,UUID user) {
        if(assignment==null)throw new AccessDeniedException("POS session has no area assignment; log in again");
        var found=jdbc.query(STAFF_SELECT+"WHERE a.assignment_id=? AND cs.user_id=?",STAFF,assignment,user);
        if(found.isEmpty())throw new AccessDeniedException("POS area assignment belongs to another employee or no longer exists");
        var result=found.getFirst();area(terminal.shipId(),result.areaId(),true,true);requireAreaLink(terminal,result.areaId());
        var active=jdbc.query(STAFF_SELECT+"WHERE a.assignment_id=? AND cs.user_id=?"+QUALIFIED,STAFF,assignment,user,terminal.shipId());
        if(active.isEmpty())throw new AccessDeniedException("Employee area/cruise assignment is inactive, expired or revoked");
        return active.getFirst();
    }
    private PosDtos.Terminal device(UUID id) {
        return repository.device(id,true).orElseThrow(()->new ResourceNotFoundException("POS terminal",id)).terminal();
    }
    private void requireAreaLink(PosDtos.Terminal terminal,UUID area) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"PosTerminalArea\" WHERE terminal_id=? AND area_object_id=? AND enabled)",Boolean.class,terminal.terminalId(),area)))
            throw new AccessDeniedException("Area is not enabled on this POS terminal");
    }
    private void area(UUID ship,UUID area,boolean lock,boolean published) {
        var row=repository.area(ship,area,lock).orElseThrow(()->new ResourceNotFoundException("Physical area in ship",area));
        if(published && !"READY".equals(row.modelStatus()))throw invalid("POS_AREA_NOT_PUBLISHED","POS requires a physical area in a READY model");
    }
    private static BusinessException invalid(String code,String message) {return new BusinessException(code,message);}
}
