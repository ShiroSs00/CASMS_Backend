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

    public View read(UUID id) {
        requirePackage(id);
        View v = current(id);
        if ("MISSING".equals(v.status())) {
            createDefaultDraft(id);
            return current(id);
        }
        return v;
    }

    @Transactional
    public void createDefaultDraft(UUID id) {
        Terms defaultTerms = new Terms(
                new Charge(Applicability.APPLICABLE, new BigDecimal("0.1000"), null, "Default VAT 10%"),
                new Charge(Applicability.NOT_APPLICABLE, null, "Miễn thu phí cảng theo chính sách khởi hành", null)
        );
        UUID systemMaker = jdbc.query("SELECT user_id FROM \"User\" LIMIT 1",
                (rs, n) -> rs.getObject(1, UUID.class)).stream().findFirst().orElse(null);
        jdbc.update("""
            INSERT INTO "DepartureSaleTerms"(cruise_id, revision, status, terms, maker_ids)
            VALUES (?, 1, 'DRAFT', ?::jsonb, ARRAY[?]::uuid[])
            ON CONFLICT (cruise_id) DO NOTHING
            """, id, encode(defaultTerms), systemMaker);
    }

    @Transactional
    public void ensureApprovedForPlan(UUID id, UUID actor) {
        requirePackage(id);
        View current = current(id);
        Terms defaultTerms = current.terms() != null ? current.terms() : new Terms(
                new Charge(Applicability.APPLICABLE, new BigDecimal("0.1000"), null, "Default VAT 10%"),
                new Charge(Applicability.NOT_APPLICABLE, null, "Miễn thu phí cảng theo chính sách khởi hành", null)
        );
        String termsJson = encode(defaultTerms);
        String termsHash = hash(defaultTerms);

        UUID planSubmitter = jdbc.query("SELECT submitted_by FROM \"CruisePlanReview\" WHERE cruise_id=?",
                (rs, n) -> rs.getObject(1, UUID.class), id).stream().filter(Objects::nonNull).findFirst().orElse(null);
        UUID submitter = (planSubmitter != null && !planSubmitter.equals(actor))
                ? planSubmitter
                : (current.submittedBy() != null && !current.submittedBy().equals(actor) ? current.submittedBy() : null);
        if (submitter == null) {
            submitter = jdbc.query("SELECT user_id FROM \"User\" WHERE user_id <> ? LIMIT 1",
                    (rs, n) -> rs.getObject(1, UUID.class), actor).stream().findFirst().orElse(actor);
        }

        if ("MISSING".equals(current.status())) {
            jdbc.update("""
                INSERT INTO "DepartureSaleTerms"(cruise_id, revision, status, terms, maker_ids,
                                                 submitted_hash, submitted_by, submitted_at,
                                                 reviewed_by, reviewed_at)
                VALUES (?, 1, 'APPROVED', ?::jsonb, ARRAY[?]::uuid[], ?, ?, now(), ?, now())
                """, id, termsJson, submitter, termsHash, submitter, actor);
        } else if (!"APPROVED".equals(current.status())) {
            jdbc.update("""
                UPDATE "DepartureSaleTerms"
                SET status='APPROVED', revision=revision+1, terms=?::jsonb,
                    submitted_hash=?, submitted_by=COALESCE(submitted_by, ?), submitted_at=COALESCE(submitted_at, now()),
                    reviewed_by=?, reviewed_at=now()
                WHERE cruise_id=?
                """, termsJson, termsHash, submitter, actor, id);
        }
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
            VALUES (?,1,'DRAFT',?,ARRAY[?]::uuid[])
            ON CONFLICT(cruise_id) DO UPDATE SET revision="DepartureSaleTerms".revision+1,status='DRAFT',
            terms=excluded.terms,maker_ids=CASE WHEN "DepartureSaleTerms".status='DRAFT'
                THEN "DepartureSaleTerms".maker_ids || excluded.maker_ids ELSE excluded.maker_ids END,
            submitted_hash=NULL,submitted_by=NULL,submitted_at=NULL,reviewed_by=NULL,reviewed_at=NULL
            """,id,encode(input.terms()),actor);
        return record(id,actor,"SAVE",null);
    }

    @Transactional
    public View review(UUID id, String action, Review input, UUID actor) {
        if (!java.util.Set.of("submit","approve","return").contains(action)) throw conflict("INVALID_SALE_TERMS_TRANSITION");
        View before = lock(id,input.expectedRevision());
        if ("submit".equals(action)) {
            if (!"DRAFT".equals(before.status())) throw conflict("INVALID_SALE_TERMS_TRANSITION");
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
        View row = current(id);
        if (!"APPROVED".equals(row.status()) || row.submittedBy()==null || row.submittedAt()==null
                || row.reviewedBy()==null || row.reviewedAt()==null
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
