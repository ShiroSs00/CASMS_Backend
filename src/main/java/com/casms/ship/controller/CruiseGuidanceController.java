package com.casms.ship.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseMainflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/cruise-plans/{cruiseId}/guidance")
@PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_READ')")
@RequiredArgsConstructor
@Transactional
public class CruiseGuidanceController {
    private final JdbcTemplate jdbc;
    private final CruiseMainflowService plans;
    public record Input(@NotBlank String sessionName,@NotBlank String location,@NotNull Instant startAt,
                        @NotNull Instant endAt,@NotNull UUID instructorUserId,String content,@NotNull @Positive Integer capacity) {}
    public record Session(UUID sessionId,UUID cruiseId,long revision,String sessionName,String location,
                          Instant startAt,Instant endAt,UUID instructorUserId,String content,Integer capacity,String status) {}
    private Instant instant(Object x){return x instanceof Timestamp t?t.toInstant():x instanceof java.time.OffsetDateTime t?t.toInstant():null;}
    private Session view(Map<String,Object> r){return new Session((UUID)r.get("guidance_session_id"),(UUID)r.get("cruise_id"),((Number)r.get("revision")).longValue(),(String)r.get("session_name"),(String)r.get("location"),instant(r.get("start_at")),instant(r.get("end_at")),(UUID)r.get("instructor_user_id"),(String)r.get("content"),(Integer)r.get("capacity"),(String)r.get("status"));}
    private Map<String,Object> lock(UUID id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM \"Cruise\" WHERE cruise_id=? FOR UPDATE",id);if(rows.isEmpty())throw new com.casms.common.exception.ResourceNotFoundException("Cruise",id);return rows.getFirst();}
    private Map<String,Object> session(UUID cruiseId,UUID id,Long expected){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM \"GuidanceSession\" WHERE cruise_id=? AND guidance_session_id=? FOR UPDATE",cruiseId,id);
        if(rows.isEmpty())throw new BusinessException(HttpStatus.FORBIDDEN,"CROSS_CRUISE_ACCESS","Session outside requested Cruise");
        Map<String,Object> row=rows.getFirst();
        if(expected==null||expected!=((Number)row.get("revision")).longValue())throw new BusinessException(HttpStatus.CONFLICT,"STALE_REVISION","Session changed");
        if(Set.of("Completed","Cancelled").contains(row.get("status")))throw new BusinessException(HttpStatus.CONFLICT,"GUIDANCE_LOCKED","Session is locked");
        return row;
    }
    private void validate(UUID cruiseId,UUID sessionId,Input r,Map<String,Object> cruise){
        Instant opens=instant(cruise.get("guidance_opens_at")),closes=instant(cruise.get("guidance_closes_at"));
        if(opens==null||closes==null||!r.startAt().isBefore(r.endAt())||r.startAt().isBefore(opens)||r.endAt().isAfter(closes))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_GUIDANCE_WINDOW","Session must be inside configured guidance window");
        Boolean eligible=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"User\" u JOIN \"UserRole\" r USING(user_id) WHERE u.user_id=? AND u.status='ACTIVE' AND r.role IN ('OPERATIONS_MANAGER','CHECKIN_STAFF','ONBOARD_STAFF','ACTIVITY_SERVICE_MANAGER'))",Boolean.class,r.instructorUserId());
        if(!Boolean.TRUE.equals(eligible))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"GUIDANCE_STAFF_INELIGIBLE","Eligible active staff required");
        Boolean overlap=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"GuidanceSession\" WHERE instructor_user_id=? AND status<>'Cancelled' AND guidance_session_id<>? AND start_at<? AND end_at>?)",Boolean.class,r.instructorUserId(),sessionId,Timestamp.from(r.endAt()),Timestamp.from(r.startAt()));
        if(Boolean.TRUE.equals(overlap))throw new BusinessException(HttpStatus.CONFLICT,"GUIDANCE_STAFF_CONFLICT","Guidance instructor has overlapping session");
    }
    @GetMapping
    @Transactional(readOnly=true)
    public ApiResponse<List<Session>> list(@PathVariable UUID cruiseId){plans.get(cruiseId);return ApiResponse.success(jdbc.queryForList("SELECT * FROM \"GuidanceSession\" WHERE cruise_id=? ORDER BY start_at",cruiseId).stream().map(this::view).toList());}
    public record Instructor(UUID id,String username) {}
    @GetMapping("/instructors")
    @Transactional(readOnly=true)
    public ApiResponse<List<Instructor>> instructors(@PathVariable UUID cruiseId){
        plans.get(cruiseId);
        return ApiResponse.success(jdbc.query("SELECT DISTINCT u.user_id,u.username FROM \"User\" u JOIN \"UserRole\" r USING(user_id) WHERE u.status='ACTIVE' AND r.role IN ('OPERATIONS_MANAGER','CHECKIN_STAFF','ONBOARD_STAFF','ACTIVITY_SERVICE_MANAGER') ORDER BY u.username",(rs,n)->new Instructor(rs.getObject(1,UUID.class),rs.getString(2))));
    }
    @PostMapping
    public ApiResponse<Session> create(@PathVariable UUID cruiseId,@Valid @RequestBody Input input,@AuthenticationPrincipal CasmsPrincipal principal){
        Map<String,Object> cruise=lock(cruiseId);UUID id=UUID.randomUUID();validate(cruiseId,id,input,cruise);
        jdbc.update("INSERT INTO \"GuidanceSession\"(guidance_session_id,cruise_id,session_name,location,start_at,end_at,instructor_user_id,status,content,capacity) VALUES(?,?,?,?,?,?,?,'Scheduled',?,?)",id,cruiseId,input.sessionName(),input.location(),Timestamp.from(input.startAt()),Timestamp.from(input.endAt()),input.instructorUserId(),input.content(),input.capacity());
        plans.recordOperationalEvent(cruiseId,"GUIDANCE_CREATE",principal.getUserId());return ApiResponse.success(read(id));
    }
    @PutMapping("/{id}")
    public ApiResponse<Session> update(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected,@Valid @RequestBody Input input,@AuthenticationPrincipal CasmsPrincipal principal){
        Map<String,Object> cruise=lock(cruiseId);session(cruiseId,id,expected);validate(cruiseId,id,input,cruise);
        jdbc.update("UPDATE \"GuidanceSession\" SET session_name=?,location=?,start_at=?,end_at=?,instructor_user_id=?,content=?,capacity=?,status='Scheduled',published_at=NULL,published_by=NULL,revision=revision+1 WHERE guidance_session_id=?",input.sessionName(),input.location(),Timestamp.from(input.startAt()),Timestamp.from(input.endAt()),input.instructorUserId(),input.content(),input.capacity(),id);
        plans.recordOperationalEvent(cruiseId,"GUIDANCE_UPDATE",principal.getUserId());return ApiResponse.success(read(id));
    }
    @PostMapping("/{id}/publish")
    public ApiResponse<Session> publish(@PathVariable UUID cruiseId,@PathVariable UUID id,@RequestHeader("If-Match") Long expected,@AuthenticationPrincipal CasmsPrincipal principal){
        Map<String,Object> cruise=lock(cruiseId),row=session(cruiseId,id,expected);Session s=view(row);
        if(s.content()==null||s.content().isBlank()||s.capacity()==null)throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"GUIDANCE_CONTENT_REQUIRED","Content and capacity required");
        validate(cruiseId,id,new Input(s.sessionName(),s.location(),s.startAt(),s.endAt(),s.instructorUserId(),s.content(),s.capacity()),cruise);
        jdbc.update("UPDATE \"GuidanceSession\" SET status='Published',published_at=now(),published_by=?,revision=revision+1 WHERE guidance_session_id=?",principal.getUserId(),id);
        plans.recordOperationalEvent(cruiseId,"GUIDANCE_PUBLISH",principal.getUserId());return ApiResponse.success(read(id));
    }
    private Session read(UUID id){return view(jdbc.queryForMap("SELECT * FROM \"GuidanceSession\" WHERE guidance_session_id=?",id));}
}
