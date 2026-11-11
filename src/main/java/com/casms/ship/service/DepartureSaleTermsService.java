package com.casms.ship.service;

import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class DepartureSaleTermsService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditRepository audit;

    public DepartureSaleTermsService(JdbcTemplate jdbc, ObjectMapper mapper, AuditRepository audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    public enum Applicability { APPLICABLE, NOT_APPLICABLE }
    public record Charge(@NotNull Applicability applicability, BigDecimal value, String reason, String evidence) {}
    public record Terms(
            @io.swagger.v3.oas.annotations.media.Schema(description = "Tax rate as a fraction from 0 to 1 when applicable")
            @NotNull @Valid Charge tax,
            @io.swagger.v3.oas.annotations.media.Schema(description = "Port fee in USD per passenger when applicable")
            @NotNull @Valid Charge portFee) {}
    public record Write(@NotNull Long expectedRevision, @NotNull @Valid Terms terms) {}
    public record Review(@NotNull Long expectedRevision, String reason) {}
    public record View(long revision, String status, Terms terms, String submittedHash,
                       UUID submittedBy, OffsetDateTime submittedAt, UUID reviewedBy, OffsetDateTime reviewedAt, String planStatus) {}
    public record SetupRequest(UUID cruiseId, String cruiseCode, String cruiseName,
                               String departurePort, String returnPort, java.util.List<String> ports) {}

    @Transactional
    public boolean requestSetup(UUID id, UUID actor) {
        jdbc.queryForObject("SELECT cruise_id FROM \"Cruise\" WHERE cruise_id=? FOR UPDATE", UUID.class, id);
        requirePackage(id);
        View view = current(id);
        if (!java.util.Set.of("DRAFT", "WITHDRAWN", "REVISION_REQUESTED").contains(view.planStatus())
                || !java.util.Set.of("MISSING", "RETURNED").contains(view.status())) {
            throw conflict("INVALID_SALE_TERMS_TRANSITION");
        }
        jdbc.update("""
                INSERT INTO "CruiseSaleTermsRequest"(cruise_id, requested_by) VALUES (?,?)
                ON CONFLICT (cruise_id) DO UPDATE SET requested_by=excluded.requested_by,
                    requested_at=now(), completed_at=NULL
                WHERE "CruiseSaleTermsRequest".completed_at IS NOT NULL
                """, id, actor);
        return true;
    }

    public boolean pendingSetup(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseSaleTermsRequest" r JOIN "Cruise" c USING(cruise_id)
                  WHERE r.cruise_id=? AND r.completed_at IS NULL AND c.status IN ('DRAFT','PUBLISHED'))
                """, Boolean.class, id));
    }

    public java.util.List<SetupRequest> setupRequests() {
        return jdbc.query("""
                SELECT c.cruise_id, c.cruise_code, c.cruise_name, departure.port_name, returning.port_name,
                    ARRAY(SELECT DISTINCT p.port_name FROM "Itinerary" i
                          JOIN "CruiseStop" s USING(itinerary_id) JOIN "Port" p ON p.port_id=s.port_id
                          WHERE i.cruise_id=c.cruise_id ORDER BY p.port_name)
                FROM "CruiseSaleTermsRequest" r JOIN "Cruise" c USING(cruise_id)
                JOIN "Port" departure ON departure.port_id=c.departure_port_id
                JOIN "Port" returning ON returning.port_id=c.return_port_id
                WHERE r.completed_at IS NULL AND c.status IN ('DRAFT','PUBLISHED')
                ORDER BY r.requested_at
                """, (row, index) -> new SetupRequest(row.getObject(1, UUID.class), row.getString(2),
                    row.getString(3), row.getString(4), row.getString(5),
                    java.util.Arrays.asList((String[]) row.getArray(6).getArray())));
    }

    public View read(UUID id) {
        requirePackage(id);
        return current(id);
    }

    /** The caller has checked the submitted whole-plan snapshot and reviewer authority. */
    @Transactional
    public void ensureApprovedForPlan(UUID id, UUID actor) {
        requirePackage(id);
        View before = current(id);
        if ("APPROVED".equals(before.status())) {
            approvedForBooking(id);
            return;
        }
        if (!"PENDING_APPROVAL".equals(before.status()) || before.terms() == null)
            throw conflict("SALE_TERMS_REQUIRED");
        validate(before.terms().tax(), true);
        validate(before.terms().portFee(), false);
        String termsHash = hash(before.terms());
        if (!Objects.equals(termsHash, before.submittedHash()))
            throw conflict("SALE_TERMS_SNAPSHOT_STALE");
        if (before.submittedBy() == null || before.submittedAt() == null) throw conflict("SALE_TERMS_SNAPSHOT_STALE");
        if (actor.equals(before.submittedBy()) || Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT ? = ANY(maker_ids) FROM "DepartureSaleTerms" WHERE cruise_id=?
                """, Boolean.class, actor, id))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "Sale terms maker cannot review this revision");
        }
        int updated = jdbc.update("""
                UPDATE "DepartureSaleTerms"
                SET status='APPROVED', revision=revision+1, reviewed_by=?, reviewed_at=now()
                WHERE cruise_id=? AND revision=?
                """, actor, id, before.revision());
        if (updated != 1) throw conflict("SALE_TERMS_SNAPSHOT_STALE");
        record(id, actor, "APPROVE_WITH_PLAN", "Phê duyệt đồng bộ cùng kế hoạch khởi hành");
    }

    private View current(UUID id) {
        String planStatus = jdbc.query("SELECT status FROM \"CruisePlanReview\" WHERE cruise_id=?",
                (row,n) -> row.getString(1), id).stream().findFirst().orElse("DRAFT");
        return jdbc.query("""
            SELECT revision,status,terms,submitted_hash,submitted_by,submitted_at,reviewed_by,reviewed_at
            FROM "DepartureSaleTerms" WHERE cruise_id=?
            """, (r,n) -> new View(r.getLong(1),r.getString(2),decode(r.getString(3)),r.getString(4),
                r.getObject(5,UUID.class),r.getObject(6,OffsetDateTime.class),
                r.getObject(7,UUID.class),r.getObject(8,OffsetDateTime.class),planStatus), id)
                .stream().findFirst().orElse(new View(0,"MISSING",null,null,null,null,null,null,planStatus));
    }

    static final String PACKAGE_MANAGED_SQL = """
        SELECT EXISTS(SELECT 1 FROM "Cruise" c WHERE cruise_id=? AND
          (source_version_id IS NOT NULL OR EXISTS(SELECT 1 FROM "DepartureFarePackage" f WHERE f.cruise_id=c.cruise_id)))
        """;

    private void requirePackage(UUID id) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(PACKAGE_MANAGED_SQL, Boolean.class, id)))
            throw conflict("CRUISE_PACKAGE_PRICING_REQUIRED");
    }

    public record HistoryEntry(UUID actorId, String action, java.time.Instant recordedAt,
                               com.fasterxml.jackson.databind.JsonNode evidence) {}

    public java.util.List<HistoryEntry> history(UUID id) {
        requirePackage(id);
        return jdbc.query("""
            SELECT actor_user_id,action,occurred_at,detail FROM audit_log
            WHERE entity_type='DepartureSaleTerms' AND entity_id=?
            ORDER BY occurred_at DESC,audit_id DESC
            """,(row,index)->new HistoryEntry(row.getObject(1,UUID.class),row.getString(2),
                row.getObject(3,OffsetDateTime.class).toInstant(),decodeEvidence(row.getString(4))),id);
    }

    private JsonNode decodeEvidence(String value) {
        try { return mapper.readTree(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot decode sale terms evidence",ex); }
    }

    private View lock(UUID id, Long expected) {
        jdbc.queryForObject("SELECT cruise_id FROM \"Cruise\" WHERE cruise_id=? FOR UPDATE", UUID.class, id);
        requirePackage(id);
        View current = current(id);
        if (!Objects.equals(expected,current.revision())) throw conflict("STALE_REVISION");
        return current;
    }

    @Transactional
    public View save(UUID id, Write input, UUID actor) {
        View before = lock(id,input.expectedRevision());
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruisePlanReview"
                WHERE cruise_id=? AND status IN ('SUBMITTED','APPROVED'))
                """, Boolean.class, id))) throw conflict("PLAN_FROZEN");
        if ("PENDING_APPROVAL".equals(before.status())) throw conflict("SALE_TERMS_PENDING");
        validate(input.terms().tax(),true);
        validate(input.terms().portFee(),false);
        jdbc.update("""
            INSERT INTO "DepartureSaleTerms"(cruise_id,revision,status,terms,maker_ids)
            VALUES (?,1,'DRAFT',?::jsonb,ARRAY[?]::uuid[])
            ON CONFLICT(cruise_id) DO UPDATE SET revision="DepartureSaleTerms".revision+1,status='DRAFT',
            terms=excluded.terms,maker_ids=CASE WHEN "DepartureSaleTerms".status='DRAFT'
                THEN "DepartureSaleTerms".maker_ids || excluded.maker_ids ELSE excluded.maker_ids END,
            submitted_hash=NULL,submitted_by=NULL,submitted_at=NULL,reviewed_by=NULL,reviewed_at=NULL
            """,id,encode(input.terms()),actor);
        jdbc.update("""
                UPDATE "CruiseSaleTermsRequest" SET completed_at=now() WHERE cruise_id=? AND completed_at IS NULL
                """, id);
        return record(id,actor,"SAVE",null);
    }

    @Transactional
    public View review(UUID id, String action, Review input, UUID actor) {
        if (!java.util.Set.of("submit","approve","return").contains(action)) throw conflict("INVALID_SALE_TERMS_TRANSITION");
        View before = lock(id,input.expectedRevision());
        if ("submit".equals(action)) {
            if (!"DRAFT".equals(before.status())) throw conflict("INVALID_SALE_TERMS_TRANSITION");
            validate(before.terms().tax(), true);
            validate(before.terms().portFee(), false);
            jdbc.update("""
                UPDATE "DepartureSaleTerms" SET status='PENDING_APPROVAL',revision=revision+1,
                submitted_hash=?,submitted_by=?,submitted_at=now() WHERE cruise_id=?
                """,hash(before.terms()),actor,id);
        } else {
            if (!"PENDING_APPROVAL".equals(before.status())) throw conflict("INVALID_SALE_TERMS_TRANSITION");
            if (actor.equals(before.submittedBy()) || Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT ? = ANY(maker_ids) FROM "DepartureSaleTerms" WHERE cruise_id=?
                """,Boolean.class,actor,id))) {
                throw new BusinessException(HttpStatus.FORBIDDEN,"SELF_APPROVAL_FORBIDDEN","Sale terms maker cannot review this revision");
            }
            if (!Objects.equals(before.submittedHash(),hash(before.terms()))) throw conflict("SALE_TERMS_SNAPSHOT_STALE");
            if ("return".equals(action) && (input.reason()==null || input.reason().isBlank()))
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"REASON_REQUIRED","A return reason is required");
            jdbc.update("""
                UPDATE "DepartureSaleTerms" SET status=?,revision=revision+1,reviewed_by=?,reviewed_at=now() WHERE cruise_id=?
                ""","approve".equals(action)?"APPROVED":"RETURNED",actor,id);
        }
        return record(id,actor,action.toUpperCase(java.util.Locale.ROOT),input.reason());
    }

    @Transactional
    void recallForPlan(UUID id, UUID actor, String reason) {
        if (!"PENDING_APPROVAL".equals(current(id).status())) return;
        jdbc.update("""
                UPDATE "DepartureSaleTerms" SET status='RETURNED',revision=revision+1
                WHERE cruise_id=? AND status='PENDING_APPROVAL'
                """, id);
        record(id, actor, "RETURN_WITH_PLAN", reason);
    }

    public String approvedFingerprint(UUID id) {
        return approvedFingerprint(current(id));
    }

    public View approvedForBooking(UUID id) {
        View row = current(id);
        if (approvedFingerprint(row) == null) throw conflict("SALE_TERMS_APPROVAL_REQUIRED");
        validate(row.terms().tax(), true);
        validate(row.terms().portFee(), false);
        return row;
    }

    private String approvedFingerprint(View row) {
        if (!"APPROVED".equals(row.status()) || row.submittedBy()==null || row.submittedAt()==null
                || row.reviewedBy()==null || row.reviewedAt()==null || row.submittedBy().equals(row.reviewedBy())
                || row.terms()==null || row.terms().tax()==null || row.terms().portFee()==null
                || !Objects.equals(row.submittedHash(),hash(row.terms()))) return null;
        return row.revision()+":"+row.submittedHash();
    }

    private View record(UUID id, UUID actor, String action, String reason) {
        View view = current(id);
        var event = new AuditLog();
        event.setActorUserId(actor);
        event.setEntityType("DepartureSaleTerms");
        event.setEntityId(id);
        event.setAction(action);
        event.setResult("SUCCESS");
        event.setDetail(encode(java.util.Map.of("snapshot",view,"reason",Objects.toString(reason,""))));
        audit.save(event);
        return view;
    }

    private void validate(Charge charge, boolean tax) {
        if (charge == null) throw conflict(tax ? "INVALID_TAX_APPLICABILITY" : "INVALID_PORT_FEE_APPLICABILITY");
        boolean valid = charge.applicability()==Applicability.APPLICABLE
                ? charge.value()!=null && charge.evidence()!=null && !charge.evidence().isBlank() && charge.value().signum()>=0 && (!tax || charge.value().compareTo(BigDecimal.ONE)<=0)
                : charge.applicability()==Applicability.NOT_APPLICABLE && charge.value()==null
                    && charge.reason()!=null && !charge.reason().isBlank();
        if (!valid) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                tax?"INVALID_TAX_APPLICABILITY":"INVALID_PORT_FEE_APPLICABILITY",
                "Configure the applicable value or explicitly document why the charge does not apply");
    }

    private String hash(Terms terms) { return CruiseMainflowService.hash(encode(terms)); }

    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot encode sale terms",ex); }
    }

    private Terms decode(String value) {
        try { return mapper.readValue(value,Terms.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot decode sale terms",ex); }
    }

    private BusinessException conflict(String code) {
        return new BusinessException(HttpStatus.CONFLICT,code,code);
    }
}
