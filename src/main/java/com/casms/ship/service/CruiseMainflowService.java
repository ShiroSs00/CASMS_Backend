package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Authoritative whole-plan review and FN-042 evidence, independent from template/pricing/lifecycle. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CruiseMainflowService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final org.springframework.beans.factory.ObjectProvider<CruiseService> cruiseServices;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<CruisePricingPolicyService> pricingServices;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<CruisePreparationService> preparationServices;

    public record Action(Long expectedRevision, String reason, String fingerprint,
                         String preparationEvidence, Map<String, Long> preparationRevisions) {
        public Action(Long expectedRevision, String reason, String fingerprint) {
            this(expectedRevision, reason, fingerprint, null, null);
        }
    }
    public record Windows(Long expectedRevision, Instant bookingOpensAt, Instant bookingClosesAt,
                          Instant guidanceOpensAt, Instant guidanceClosesAt) {}
    public record AssignmentContext(UUID cruiseId,long cruiseRevision,boolean hasBookings,boolean editablePlan) {}
    /** Minimal safety fact for CRM ship assignment; never exposes the passenger manifest. */
    public AssignmentContext assignmentContext(UUID id) {
        Map<String,Object> c=cruise(id,false);
        boolean editable="DRAFT".equals(c.get("status"))&&Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(status(row(id)));
        boolean hasBookings=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Booking\" WHERE cruise_id=?)",Boolean.class,id));
        return new AssignmentContext(id,((Number)c.get("revision")).longValue(),hasBookings,editable);
    }
    public record View(UUID cruiseId, long revision, String planStatus, Long submittedRevision, String cruiseCode, String submittedByName, UUID submittedBy, Instant submittedAt,
                       UUID reviewedBy, Instant reviewedAt, String reason, JsonNode submittedSnapshot,
                       String fingerprint, String readyStatus, UUID readyBy, Instant readyAt,
                       List<String> planBlockers, List<String> readyBlockers, List<String> allowedActions, Windows windows, List<ProgramIssue> programIssues) {}
    public record ProgramIssue(String code,String kind,UUID itemId,String name,Instant startsAt,Instant endsAt,Instant earliestStart,Instant latestEnd,String timeZoneId) {}

    private Map<String,Object> cruise(UUID id, boolean lock) {
        List<Map<String,Object>> found = jdbc.queryForList("SELECT * FROM \"Cruise\" WHERE cruise_id=?" + (lock ? " FOR UPDATE" : ""), id);
        if(found.isEmpty()) throw new ResourceNotFoundException("Cruise", id);
        return found.getFirst();
    }
    private Map<String,Object> row(UUID id) {
        List<Map<String,Object>> found=jdbc.queryForList("SELECT * FROM \"CruisePlanReview\" WHERE cruise_id=?", id);
        return found.isEmpty()?Map.of():found.getFirst();
    }
    private long revision(Map<String,Object> r) { return r.isEmpty()?0:((Number)r.get("revision")).longValue(); }
    private String status(Map<String,Object> r) { return (String)r.getOrDefault("status","DRAFT"); }
    private Instant time(Object t) { return t instanceof java.sql.Timestamp x?x.toInstant():t instanceof java.time.OffsetDateTime x?x.toInstant():null; }
    private JsonNode json(Object value) {
        try { return value==null?null:mapper.readTree(value.toString()); }
        catch(Exception ex) { throw new IllegalStateException("Persisted mainflow evidence is invalid", ex); }
    }
    private String aggregate(String sql, UUID id) {
        return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(v ORDER BY v::text),'[]'::jsonb)::text FROM ("+sql+") q(v)",String.class,id);
    }
    /** Only CRM-owned inputs: staff, preparations and attendance do not gate plan submission. */
    public String planSnapshot(UUID id) {
        Map<String,Object> out = new TreeMap<>();
        String c=jdbc.queryForObject("SELECT (to_jsonb(c)-ARRAY['status','revision','created_at','updated_at','opened_for_sale_at','opened_for_sale_by','sale_event_identity','sale_scope','stopped_at','stopped_by','stop_reason','stop_evidence','stop_scope','stop_event_identity','actual_departure_datetime','actual_return_datetime','departure_actor_id','completion_actor_id','closure_notes','preparation_coordinator_id'])::text FROM \"Cruise\" c WHERE cruise_id=?",String.class,id);
        out.put("cruise",json(c));
        out.put("itinerary",json(aggregate("SELECT to_jsonb(i)-ARRAY['status','approval_inherited','approved_at','approved_by_user_id','content_modified_after_materialization','created_at','updated_at'] FROM \"Itinerary\" i WHERE cruise_id=?",id)));
        out.put("days",json(aggregate("SELECT to_jsonb(d) FROM \"ItineraryDay\" d JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=?",id)));
        out.put("stops",json(aggregate("SELECT to_jsonb(s)-ARRAY['actual_arrival','actual_departure','updated_at'] FROM \"CruiseStop\" s JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=?",id)));
        out.put("activities",json(aggregate("SELECT to_jsonb(a)-ARRAY['updated_at','status'] FROM \"OnboardActivity\" a WHERE cruise_id=?",id)));
        out.put("excursions",json(aggregate("SELECT to_jsonb(e)-ARRAY['updated_at','status'] FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=?",id)));
        out.put("policy",json(aggregate("SELECT to_jsonb(p)-ARRAY['status','approved_at','approved_by','created_at','updated_at','revision','submitted_by','submitted_at','reviewed_by','reviewed_at','decision_reason','submitted_snapshot','snapshot_hash'] FROM \"CruisePricingPolicy\" p WHERE cruise_id=? AND policy_number=(SELECT max(policy_number) FROM \"CruisePricingPolicy\" WHERE cruise_id=p.cruise_id)",id)));
        out.put("packages",json(aggregate("SELECT jsonb_build_object('assignment',to_jsonb(c)-'is_active','package',to_jsonb(p)) FROM \"CruiseTourPackage\" c JOIN \"TourPackage\" p USING(tour_package_id) WHERE cruise_id=?",id)));
        for(String table:List.of("TourPackageService","TourPackageActivity","TourPackageExcursion"))
            out.put(table,json(aggregate("SELECT to_jsonb(t) FROM \""+table+"\" t JOIN \"CruiseTourPackage\" c USING(tour_package_id) WHERE c.cruise_id=?",id)));
        out.put("servingWindows",json(aggregate("SELECT to_jsonb(s)-ARRAY['created_at','updated_at'] FROM \"ServiceSchedule\" s WHERE cruise_id=?",id)));
        out.put("services",json(aggregate("SELECT to_jsonb(s)-ARRAY['created_at','updated_at'] FROM \"Service\" s WHERE EXISTS(SELECT 1 FROM \"ServiceSchedule\" w WHERE w.service_id=s.service_id AND w.cruise_id=?)",id)));
        out.put("ports",json(aggregate("SELECT to_jsonb(p)-ARRAY['created_at','updated_at'] FROM \"Port\" p WHERE EXISTS(SELECT 1 FROM \"CruiseStop\" s JOIN \"Itinerary\" i USING(itinerary_id) WHERE s.port_id=p.port_id AND i.cruise_id=?)",id)));
        out.put("suppliers",json(aggregate("SELECT to_jsonb(p)-ARRAY['created_at','updated_at'] FROM \"Supplier\" p WHERE p.supplier_id IN (SELECT e.supplier_id FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=?)",id)));
        try { return mapper.writeValueAsString(out); } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    @Transactional
    public void recordOperationalEvent(UUID id,String action,UUID actor) {
        Map<String,Object> evidence=new TreeMap<>();evidence.put("plan",json(planSnapshot(id)));
        for(String table:List.of("CruisePreparationSection","CruiseStaff","GuidanceSession"))
            evidence.put(table,json(aggregate("SELECT to_jsonb(t) FROM \""+table+"\" t WHERE cruise_id=?",id)));
        try{event(id,action,actor,null,mapper.writeValueAsString(evidence));}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException(ex);}
    }
    public static String hash(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    private String readinessFingerprint(UUID id) {
        StringBuilder evidence=new StringBuilder(planSnapshot(id));
        for(String table:List.of("CruiseStaff","CruisePreparationSection","CruiseRegulation"))
            evidence.append(aggregate("SELECT to_jsonb(t)-ARRAY['updated_at'] FROM \""+table+"\" t WHERE cruise_id=?",id));
        // Attendance/completion is downstream of sale. Publication/content/assignment remain material.
        evidence.append(aggregate("SELECT to_jsonb(t)-ARRAY['updated_at','status','revision'] FROM \"GuidanceSession\" t WHERE cruise_id=?",id));
        evidence.append(aggregate("SELECT to_jsonb(s) FROM \"Ship\" s JOIN \"Cruise\" c USING(ship_id) WHERE c.cruise_id=?",id));
        for(String table:List.of("Cabin","Deck","ShipRegulation"))
            evidence.append(aggregate("SELECT to_jsonb(t)-ARRAY['updated_at'] FROM \""+table+"\" t JOIN \"Cruise\" c USING(ship_id) WHERE c.cruise_id=?",id));
        evidence.append(aggregate("SELECT to_jsonb(r)-ARRAY['updated_at'] FROM \"OperationalRegulation\" r CROSS JOIN \"Cruise\" c WHERE c.cruise_id=? AND (EXISTS(SELECT 1 FROM \"CruiseRegulation\" a WHERE a.regulation_id=r.regulation_id AND a.cruise_id=c.cruise_id) OR EXISTS(SELECT 1 FROM \"ShipRegulation\" s WHERE s.regulation_id=r.regulation_id AND s.ship_id=c.ship_id))",id));
        return hash(evidence.toString());
    }
    private List<String> planBlockers(UUID id, Map<String,Object> c) {
        return planBlockers(id,c,programIssues(id));
    }
    private List<String> planBlockers(UUID id, Map<String,Object> c,List<ProgramIssue> issues) {
        List<String> b=new ArrayList<>();
        if(c.get("ship_id")==null)b.add("SHIP_REQUIRED");
        // Source-less legacy Cruises keep their actual schedule; never invent a template link.
        if(c.get("source_trip_id")!=null&&c.get("source_version_id")==null)b.add("SOURCE_VERSION_REQUIRED");
        if(!Set.of("DOMESTIC","INTERNATIONAL").contains(Objects.toString(c.get("planned_scope"),"")))b.add("SCOPE_COUNTRY_REQUIRED");
        if(c.get("booking_opens_at")==null||c.get("booking_closes_at")==null)b.add("BOOKING_WINDOW_REQUIRED");
        if(c.get("guidance_opens_at")==null||c.get("guidance_closes_at")==null)b.add("GUIDANCE_WINDOW_REQUIRED");
        Instant bookingClose=time(c.get("booking_closes_at")),guidanceStart=time(c.get("guidance_opens_at"));
        if(bookingClose!=null&&guidanceStart!=null&&guidanceStart.isBefore(bookingClose))b.add("GUIDANCE_BEFORE_BOOKING_CLOSE");
        if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ItineraryDay\" d JOIN \"Itinerary\" i USING(itinerary_id) WHERE cruise_id=?)",Boolean.class,id)))b.add("ITINERARY_REQUIRED");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Itinerary\" i JOIN \"Cruise\" c USING(cruise_id) WHERE c.cruise_id=? AND (i.start_date<>(c.departure_datetime AT TIME ZONE 'Asia/Ho_Chi_Minh')::date OR i.end_date<>(c.return_datetime AT TIME ZONE 'Asia/Ho_Chi_Minh')::date))",Boolean.class,id)))b.add("ITINERARY_CRUISE_DATES_MISMATCH");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Itinerary\" i WHERE i.cruise_id=? AND ((SELECT count(*) FROM \"ItineraryDay\" d WHERE d.itinerary_id=i.itinerary_id)<>i.end_date-i.start_date+1 OR EXISTS(SELECT 1 FROM \"ItineraryDay\" d WHERE d.itinerary_id=i.itinerary_id AND d.date<>i.start_date+d.day_number-1)))",Boolean.class,id)))b.add("ITINERARY_DAY_DATE_MISMATCH");
        if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruisePricingPolicy\" WHERE cruise_id=?)",Boolean.class,id)))b.add("PRICING_POLICY_REQUIRED");
        else if(!Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM "CruisePricingPolicy" p JOIN "Cruise" c USING(cruise_id)
              WHERE p.cruise_id=? AND p.policy_number=(SELECT max(policy_number) FROM "CruisePricingPolicy" WHERE cruise_id=p.cruise_id)
                AND p.currency='USD' AND p.deposit_percentage BETWEEN 0 AND 1
                AND NOT EXISTS(SELECT 1 FROM "Cabin" room WHERE room.ship_id=c.ship_id
                  AND CASE room.cabin_type::text WHEN 'INTERIOR' THEN p.interior_base_fare
                    WHEN 'OCEAN_VIEW' THEN p.ocean_view_base_fare WHEN 'BALCONY' THEN p.balcony_base_fare
                    WHEN 'SUITE' THEN p.suite_base_fare END IS NULL))
            """,Boolean.class,id)))b.add("PRICING_POLICY_CONFIGURATION_REQUIRED");
        issues.stream().map(ProgramIssue::code).distinct().forEach(b::add);
        return b;
    }
    private List<ProgramIssue> programIssues(UUID id) {
        List<ProgramIssue> issues=new ArrayList<>();
        var activities=jdbc.queryForList("SELECT a.onboard_activity_id AS id,a.activity_name AS name,a.capacity,a.price,a.currency_code,a.start_time,a.end_time,c.departure_datetime AS earliest,c.return_datetime AS latest,'Asia/Ho_Chi_Minh' AS zone FROM \"OnboardActivity\" a JOIN \"Cruise\" c USING(cruise_id) WHERE a.cruise_id=? AND a.status<>'CANCELLED' ORDER BY a.start_time,a.onboard_activity_id",id);
        for(var row:activities) collectProgramIssues(issues,row,"ACTIVITY");
        var excursions=jdbc.queryForList("SELECT e.shore_excursion_id AS id,e.excursion_name AS name,e.maximum_capacity AS capacity,e.price,e.currency_code,e.start_time,e.planned_return_time AS end_time,s.planned_arrival AS earliest,LEAST(s.planned_departure,COALESCE(s.all_aboard_time,s.planned_departure)) AS latest,p.time_zone_id AS zone FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Port\" p ON p.port_id=s.port_id JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=? AND e.status<>'CANCELLED' ORDER BY e.start_time,e.shore_excursion_id",id);
        for(var row:excursions) collectProgramIssues(issues,row,"EXCURSION");
        return issues;
    }
    private void collectProgramIssues(List<ProgramIssue> issues,Map<String,Object> row,String kind) {
        List<String> codes=new ArrayList<>();
        if(!(row.get("capacity") instanceof Number n)||n.longValue()<1)codes.add(kind+"_CAPACITY_REQUIRED");
        if(!(row.get("price") instanceof Number n)||new java.math.BigDecimal(n.toString()).signum()<0)codes.add(kind+"_PRICE_REQUIRED");
        if(!(row.get("currency_code") instanceof String currency)||!currency.trim().matches("[A-Z]{3}"))codes.add(kind+"_CURRENCY_REQUIRED");
        Instant start=time(row.get("start_time")),end=time(row.get("end_time")),earliest=time(row.get("earliest")),latest=time(row.get("latest"));
        if(start==null||end==null||earliest==null||latest==null||!end.isAfter(start)||start.isBefore(earliest)||end.isAfter(latest))
            codes.add(kind.equals("ACTIVITY")?"ACTIVITY_OUTSIDE_CRUISE":"EXCURSION_OUTSIDE_PORT_CALL");
        for(String code:codes)issues.add(new ProgramIssue(code,kind,(UUID)row.get("id"),(String)row.get("name"),start,end,earliest,latest,(String)row.get("zone")));
    }
    private boolean approved(UUID id, Map<String,Object> r) {
        return "APPROVED".equals(status(r)) && Objects.equals(r.get("fingerprint"),hash(planSnapshot(id)));
    }
    public boolean isPlanApproved(UUID id) { return approved(id, row(id)); }
    public View get(UUID id) {
        Map<String,Object> c=cruise(id,false),r=row(id);
        String fingerprint=readinessFingerprint(id);
        List<ProgramIssue> issues=programIssues(id);
        List<String> planIssues=planBlockers(id,c,issues);
        List<String> b=new ArrayList<>(planIssues);
        if(!approved(id,r))b.add("PLAN_APPROVAL_REQUIRED");
        Integer confirmed=jdbc.queryForObject("SELECT count(*) FROM \"CruisePreparationSection\" WHERE cruise_id=? AND status='CONFIRMED' AND section_code IN ('TECHNICAL_NAUTICAL','HOTEL_CABIN','ENTERTAINMENT_SHORE','SAFETY_CREW')",Integer.class,id);
        if(confirmed==null||confirmed!=4)b.add("PREPARATION_CONFIRMATION_REQUIRED");
        Boolean onboard=jdbc.queryForObject(com.casms.ship.repository.CruiseStaffRepository.ACTIVE_ONBOARD_SQL.replace(":cruiseId","?"),Boolean.class,id);
        if(!Boolean.TRUE.equals(onboard))b.add("ONBOARD_STAFF_REQUIRED");
        Boolean guidance=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"GuidanceSession\" WHERE cruise_id=? AND status IN ('Published','Completed') AND published_at IS NOT NULL AND content IS NOT NULL AND length(trim(content))>0 AND instructor_user_id IS NOT NULL)",Boolean.class,id);
        if(!Boolean.TRUE.equals(guidance))b.add("GUIDANCE_PUBLICATION_REQUIRED");
        // FN-038 checks booking consistency; no minimum bookings/check-in/attendance before sale.
        if(c.get("booking_opens_at")==null||c.get("booking_closes_at")==null)b.add("BOOKING_WINDOW_REQUIRED");
        {
            var operational=cruiseServices.getObject().validateOperationalReadiness(id);
            if(operational.getStatus()==com.casms.ship.enums.ReadinessStatus.INDETERMINATE)b.add("DEPENDENCY_TIMEOUT");
            if(!operational.isReady()) operational.getBlockers().stream().filter(code -> !"PLAN_APPROVAL_REQUIRED".equals(code)).filter(code -> !b.contains(code)).forEach(b::add);
        }
        boolean ready=r.get("ready_at")!=null;
        String readyStatus=!ready?"NOT_READY":b.isEmpty()&&Objects.equals(r.get("ready_fingerprint"),fingerprint)?"READY":"INVALIDATED";
        List<String> actions=new ArrayList<>();
        if(Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(status(r)))actions.add("SUBMIT");
        if("SUBMITTED".equals(status(r)))actions.addAll(List.of("WITHDRAW","APPROVE","REJECT","REQUEST_REVISION"));
        if(Set.of("SUBMITTED","APPROVED").contains(status(r))
                && Objects.equals(r.get("fingerprint"),hash(planSnapshot(id)))
                && preparationSubmissionPending(id))actions.add("SUBMIT");
        if(!planIssues.isEmpty())actions.remove("APPROVE");
        if("APPROVED".equals(status(r))&&(!b.isEmpty()||pricingReturned(id)))actions.add("REQUEST_REVISION");
        if(b.isEmpty())actions.add("SET_READY");
        List<String> submitterNames=r.get("submitted_by")==null?List.of():jdbc.queryForList("SELECT username FROM \"User\" WHERE user_id=?",String.class,r.get("submitted_by"));
        return new View(id,revision(r),status(r),(Long)r.get("submitted_revision"),(String)c.get("cruise_code"),submitterNames.isEmpty()?null:submitterNames.getFirst(),(UUID)r.get("submitted_by"),time(r.get("submitted_at")),
                (UUID)r.get("reviewed_by"),time(r.get("reviewed_at")),(String)r.get("reason"),json(r.get("snapshot")),
                fingerprint,readyStatus,(UUID)r.get("ready_by"),time(r.get("ready_at")),planIssues,b,actions,
                new Windows(((Number)c.get("revision")).longValue(),time(c.get("booking_opens_at")),time(c.get("booking_closes_at")),time(c.get("guidance_opens_at")),time(c.get("guidance_closes_at"))),issues);
    }
    public List<View> queue() {
        return jdbc.queryForList("SELECT cruise_id FROM \"CruisePlanReview\" WHERE status='SUBMITTED' ORDER BY submitted_at",UUID.class).stream().map(this::get).toList();
    }
    private void expected(Map<String,Object> r, Long expected) {
        if(expected==null||expected!=revision(r))throw new BusinessException(HttpStatus.CONFLICT,"STALE_REVISION","Expected plan revision must match");
    }
    private boolean pricingReturned(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruisePricingPolicy\" p WHERE p.cruise_id=? AND p.status='RETURNED' AND p.policy_number=(SELECT max(policy_number) FROM \"CruisePricingPolicy\" WHERE cruise_id=p.cruise_id))",Boolean.class,id));
    }
    @Transactional
    public void assertEditable(UUID id) {
        cruise(id,true);
        if(!Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(status(row(id))))
            throw new BusinessException(HttpStatus.CONFLICT,"PLAN_FROZEN","Withdraw or request plan revision before editing");
    }
    @Transactional
    public View windows(UUID id, Windows w, UUID actor) {
        Map<String,Object> c=cruise(id,true);assertEditable(id);
        if(w.expectedRevision()==null||!Objects.equals(w.expectedRevision(),((Number)c.get("revision")).longValue()))
            throw new BusinessException(HttpStatus.CONFLICT,"STALE_REVISION","Cruise revision changed");
        Instant departure=time(c.get("departure_datetime"));
        if(w.bookingOpensAt()==null||w.bookingClosesAt()==null||!w.bookingOpensAt().isBefore(w.bookingClosesAt())||!w.bookingClosesAt().isBefore(departure)
                ||w.guidanceOpensAt()==null||w.guidanceClosesAt()==null||!w.guidanceOpensAt().isBefore(w.guidanceClosesAt())||w.guidanceClosesAt().isAfter(departure))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_WINDOWS","Invalid booking or guidance window");
        if(w.guidanceOpensAt().isBefore(w.bookingClosesAt()))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"GUIDANCE_BEFORE_BOOKING_CLOSE","Guidance must start after booking closes");
        jdbc.update("UPDATE \"Cruise\" SET booking_opens_at=?,booking_closes_at=?,guidance_opens_at=?,guidance_closes_at=?,revision=revision+1 WHERE cruise_id=?",
                java.sql.Timestamp.from(w.bookingOpensAt()),java.sql.Timestamp.from(w.bookingClosesAt()),java.sql.Timestamp.from(w.guidanceOpensAt()),java.sql.Timestamp.from(w.guidanceClosesAt()),id);
        event(id,"CONFIGURE_WINDOWS",actor,null,planSnapshot(id));return get(id);
    }
    private void event(UUID id,String action,UUID actor,String reason,String snapshot) {
        if(actor==null)throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Authentication required");
        jdbc.update("INSERT INTO \"CruisePlanEvent\" (event_id,cruise_id,revision,action,actor_id,reason,snapshot) VALUES(?,?,?,?,?,?,?::jsonb)",UUID.randomUUID(),id,revision(row(id)),action,actor,reason,snapshot);
    }
    @Transactional
    public View transition(UUID id,String action,Action request,UUID actor) {
        Map<String,Object> c=cruise(id,true),r=row(id);expected(r,request.expectedRevision());
        String before=status(r),after;
        String snapshot=planSnapshot(id);
        switch(action) {
            case "SUBMIT" -> {
                if(Set.of("SUBMITTED","APPROVED").contains(before)) {
                    if(!Objects.equals(r.get("fingerprint"),hash(snapshot))||!preparationSubmissionPending(id))throw conflict();
                    preparationServices.getObject().submitDossier(id,request.preparationEvidence(),request.preparationRevisions(),actor);
                    jdbc.update("UPDATE \"CruisePlanReview\" SET revision=revision+1 WHERE cruise_id=?",id);
                    event(id,"SUPPLEMENT_PREPARATION",actor,null,snapshot);
                    return get(id);
                }
                if(!Set.of("DRAFT","WITHDRAWN","REVISION_REQUESTED").contains(before))throw conflict();
                List<String> blockers=planBlockers(id,c);
                if(!blockers.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"PLAN_INCOMPLETE","Plan configuration incomplete",Map.of("blockers",blockers));
                var pricing=pricingServices.getObject();
                var policy=pricing.getPricingPolicy(id);
                if("DRAFT".equals(policy.getStatus().name()))pricing.review(id,"submit",new CruisePricingPolicyService.Review(policy.getRevision(),null),actor);
                else if(!Set.of("PENDING_APPROVAL","APPROVED").contains(policy.getStatus().name()))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"PRICING_POLICY_CONFIGURATION_REQUIRED","Save pricing draft before submitting plan");
                preparationServices.getObject().submitDossier(id,request.preparationEvidence(),request.preparationRevisions(),actor);
                after="SUBMITTED";
            }
            case "WITHDRAW" -> {if(!"SUBMITTED".equals(before))throw conflict();pricingServices.getObject().recallForPlan(id,actor,false);after="WITHDRAWN";}
            case "APPROVE","REJECT","REQUEST_REVISION" -> {
                boolean reopening="REQUEST_REVISION".equals(action)&&"APPROVED".equals(before)&&(!get(id).readyBlockers().isEmpty()||pricingReturned(id));
                if(!"SUBMITTED".equals(before)&&!reopening)throw conflict();
                if(Objects.equals(actor,r.get("submitted_by")))throw new BusinessException(HttpStatus.FORBIDDEN,"SELF_APPROVAL_FORBIDDEN","Submitter cannot review its own plan");
                if(!reopening&&!Objects.equals(r.get("fingerprint"),hash(snapshot)))throw new BusinessException(HttpStatus.CONFLICT,"PLAN_SNAPSHOT_STALE","Submitted source changed");
                if(!"APPROVE".equals(action)&&(request.reason()==null||request.reason().isBlank()))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"REASON_REQUIRED","Reason or feedback required");
                if("APPROVE".equals(action)) {
                    // Review CRM-owned plan inputs here; staffing, preparation and guidance belong to FN-042.
                    var blockers=planBlockers(id,c);
                    if(!blockers.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"PLAN_APPROVAL_CHECK_FAILED","Plan approval checks incomplete",Map.of("blockers",blockers));
                } else {
                    pricingServices.getObject().recallForPlan(id,actor,true);
                }
                after=action.equals("APPROVE")?"APPROVED":action.equals("REJECT")?"REJECTED":"REVISION_REQUESTED";
            }
            default -> throw conflict();
        }
        jdbc.update("INSERT INTO \"CruisePlanReview\"(cruise_id) VALUES(?) ON CONFLICT DO NOTHING",id);
        if(action.equals("SUBMIT"))jdbc.update("UPDATE \"CruisePlanReview\" SET status=?,submitted_revision=revision+1,revision=revision+1,snapshot=?::jsonb,fingerprint=?,submitted_by=?,submitted_at=now(),reviewed_by=NULL,reviewed_at=NULL,reason=NULL,ready_at=NULL,ready_fingerprint=NULL WHERE cruise_id=?",after,snapshot,hash(snapshot),actor,id);
          else if(action.equals("WITHDRAW"))jdbc.update("UPDATE \"CruisePlanReview\" SET status=?,revision=revision+1,reviewed_by=NULL,reviewed_at=NULL,reason=NULL,ready_at=NULL,ready_fingerprint=NULL WHERE cruise_id=?",after,id);
        else jdbc.update("UPDATE \"CruisePlanReview\" SET status=?,revision=revision+1,reviewed_by=?,reviewed_at=now(),reason=?,ready_at=NULL,ready_fingerprint=NULL WHERE cruise_id=?",after,actor,request.reason(),id);
        event(id,action,actor,request.reason(),action.equals("SUBMIT")?snapshot:r.get("snapshot").toString());
        return get(id);
    }
    private BusinessException conflict() {return new BusinessException(HttpStatus.CONFLICT,"INVALID_PLAN_TRANSITION","Invalid plan transition");}
    private boolean preparationSubmissionPending(UUID id) {
        Integer sent = jdbc.queryForObject("SELECT count(*) FROM \"CruisePreparationSection\" WHERE cruise_id=? AND section_code IN ('TECHNICAL_NAUTICAL','HOTEL_CABIN','ENTERTAINMENT_SHORE','SAFETY_CREW') AND status IN ('SUBMITTED','CONFIRMED')",Integer.class,id);
        return sent == null || sent != 4;
    }
    @Transactional
    public View setReady(UUID id,Action request,UUID actor) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!(auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal)
                ||!Objects.equals(actor,principal.getUserId())||!principal.hasRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER)||!principal.hasAuthority("CRUISE_VALIDATE"))
            throw new BusinessException(HttpStatus.FORBIDDEN,"READY_ACTION_FORBIDDEN","Only authenticated OPS may confirm Ready");
        cruise(id,true);expected(row(id),request.expectedRevision());View evaluated=get(id);
        if(!Objects.equals(request.fingerprint(),evaluated.fingerprint()))throw new BusinessException(HttpStatus.CONFLICT,"READINESS_FINGERPRINT_STALE","Readiness changed");
        if(!evaluated.readyBlockers().isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"READY_CHECK_FAILED","Readiness checks incomplete",Map.of("blockers",evaluated.readyBlockers()));
        jdbc.update("UPDATE \"CruisePlanReview\" SET ready_by=?,ready_at=now(),ready_fingerprint=?,ready_evidence=?::jsonb,revision=revision+1 WHERE cruise_id=?",actor,evaluated.fingerprint(),planSnapshot(id),id);
        event(id,"SET_READY",actor,request.reason(),planSnapshot(id));return get(id);
    }
    @Transactional
    public void lockOperational(UUID id) { cruise(id,true); }
    public boolean isBookingWindowOpen(UUID id) {
        Map<String,Object> c=cruise(id,false);Instant opens=time(c.get("booking_opens_at")),closes=time(c.get("booking_closes_at"));
        Instant now=Instant.now();return opens!=null&&closes!=null&&!now.isBefore(opens)&&now.isBefore(closes);
    }
    /** U-PRICE is an explicit unresolved business decision, never satisfied by a legacy auto-approval. */
    public List<String> commercialBlockers(UUID id) {
        Boolean approved=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruisePricingPolicy\" p WHERE cruise_id=? AND policy_number=(SELECT max(policy_number) FROM \"CruisePricingPolicy\" WHERE cruise_id=p.cruise_id) AND status='APPROVED' AND currency='USD' AND submitted_snapshot IS NOT NULL AND snapshot_hash IS NOT NULL AND reviewed_by IS NOT NULL AND approved_by=reviewed_by)",Boolean.class,id);
        return Boolean.TRUE.equals(approved)?List.of():List.of("PRICING_POLICY_APPROVAL_REQUIRED");
    }
    public void requireBookable(UUID id) {
        Map<String,Object> c=cruise(id,false);requireReady(id);
        if(!commercialBlockers(id).isEmpty())throw new BusinessException(HttpStatus.CONFLICT,"PRICING_POLICY_APPROVAL_REQUIRED","Current USD pricing revision requires independent OPS approval");
        if(c.get("opened_for_sale_at")==null||!"PUBLISHED".equals(c.get("status"))||c.get("stopped_at")!=null||!isBookingWindowOpen(id)
                ||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Itinerary\" WHERE cruise_id=? AND status='OPEN_FOR_SALE')",Boolean.class,id)))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"CRUISE_NOT_OPEN_FOR_SALE","Cruise is not currently bookable");
    }
    public boolean isReady(UUID id) {return "READY".equals(get(id).readyStatus());}
    public void requireReady(UUID id) {
        if(!isReady(id))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"READY_CONFIRMATION_REQUIRED","Current persisted Ready required");
    }
}
