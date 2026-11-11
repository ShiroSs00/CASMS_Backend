package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.service.CruisePackageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CruisePlanPackageSelectionService {
    private final JdbcTemplate jdbc;
    private final CruisePackageService packages;
    private final CruiseMainflowService plans;

    public record Choice(@NotNull UUID offerId, @NotNull UUID priceRevisionId) {}
    public record Write(@NotNull @PositiveOrZero Long expectedSelectionRevision,
                        @NotNull List<@NotNull @Valid Choice> selections) {}
    public record View(UUID cruiseId, long selectionRevision, List<Choice> selections,
                       List<CruisePackageService.PlanCandidate> offers) {}

    public View read(UUID cruiseId) {
        requireUnified(cruiseId);
        return new View(cruiseId, revision(cruiseId), selections(cruiseId), packages.planCandidates(cruiseId));
    }

    @Transactional
    public View save(UUID cruiseId, Write input, UUID actor) {
        plans.assertEditable(cruiseId);
        requireUnified(cruiseId);
        if (revision(cruiseId) != input.expectedSelectionRevision())
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Lựa chọn gói đã thay đổi. Tải lại hồ sơ trước khi lưu.");
        var ids = new HashSet<UUID>();
        for (var choice : input.selections()) {
            if (!ids.add(choice.offerId()) || !packages.validPlanChoice(cruiseId, choice.offerId(), choice.priceRevisionId()))
                throw new BusinessException("FARE_PLAN_SELECTION_INVALID", "Chọn mỗi gói một lần với phiên bản giá đủ điều kiện.");
        }
        jdbc.update("""
                INSERT INTO "CruisePlanPackageSelection"(cruise_id,revision,changed_by)
                VALUES (?,1,?) ON CONFLICT(cruise_id) DO UPDATE SET
                revision="CruisePlanPackageSelection".revision+1,changed_by=excluded.changed_by,changed_at=now()
                """, cruiseId, actor);
        jdbc.update("DELETE FROM \"CruisePlanPackageSelectionItem\" WHERE cruise_id=?", cruiseId);
        for (var choice : input.selections()) {
            jdbc.update("""
                    INSERT INTO "CruisePlanPackageSelectionItem"
                    (cruise_id,offer_id,price_revision_id,template_version_id,offer_revision,commercial_revision)
                    SELECT cruise_id,offer_id,?,template_version_id,revision,commercial_revision
                    FROM "DepartureFarePackage" WHERE cruise_id=? AND offer_id=?
                    """, choice.priceRevisionId(), cruiseId, choice.offerId());
        }
        return read(cruiseId);
    }

    public List<Choice> selections(UUID cruiseId) {
        return jdbc.query("""
                SELECT offer_id,price_revision_id FROM "CruisePlanPackageSelectionItem"
                WHERE cruise_id=? ORDER BY offer_id
                """, (rs,n) -> new Choice(rs.getObject(1,UUID.class), rs.getObject(2,UUID.class)), cruiseId);
    }

    @Transactional
    public void autoPopulateIfEmpty(UUID cruiseId, UUID actor) {
        if (!selections(cruiseId).isEmpty()) return;
        var offers = jdbc.query("""
                SELECT f.offer_id, f.template_version_id, f.revision, f.commercial_revision,
                       (SELECT p.price_revision_id FROM "DepartureFarePackagePrice" p
                        WHERE p.offer_id = f.offer_id
                        ORDER BY CASE WHEN p.status = 'APPROVED' THEN 1 WHEN p.status = 'SUBMITTED' THEN 2 ELSE 3 END,
                                 p.revision_number DESC LIMIT 1) as price_revision_id
                FROM "DepartureFarePackage" f
                WHERE f.cruise_id = ?
                """, (rs, n) -> new Object[]{
                rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class),
                rs.getLong(3),
                rs.getLong(4),
                rs.getObject(5, UUID.class)
        }, cruiseId);
        if (offers.isEmpty()) return;

        UUID changedBy = actor != null ? actor : jdbc.query("SELECT user_id FROM \"User\" LIMIT 1",
                (rs, n) -> rs.getObject(1, UUID.class)).stream().findFirst().orElse(null);

        jdbc.update("""
                INSERT INTO "CruisePlanPackageSelection"(cruise_id,revision,changed_by)
                VALUES (?,1,?) ON CONFLICT(cruise_id) DO UPDATE SET
                revision="CruisePlanPackageSelection".revision+1,changed_by=excluded.changed_by,changed_at=now()
                """, cruiseId, changedBy);
        for (var row : offers) {
            UUID offerId = (UUID) row[0];
            UUID templateVersionId = (UUID) row[1];
            long offerRev = (Long) row[2];
            long commRev = (Long) row[3];
            UUID priceId = (UUID) row[4];
            if (priceId != null) {
                jdbc.update("""
                        INSERT INTO "CruisePlanPackageSelectionItem"
                        (cruise_id,offer_id,price_revision_id,template_version_id,offer_revision,commercial_revision)
                        VALUES (?,?,?,?,?,?)
                        ON CONFLICT (cruise_id, offer_id) DO UPDATE SET
                        price_revision_id=excluded.price_revision_id,
                        template_version_id=excluded.template_version_id,
                        offer_revision=excluded.offer_revision,
                        commercial_revision=excluded.commercial_revision
                        """, cruiseId, offerId, priceId, templateVersionId, offerRev, commRev);
            }
        }
    }

    public List<String> blockers(UUID cruiseId) {
        var choices = selections(cruiseId);
        if (choices.isEmpty()) {
            autoPopulateIfEmpty(cruiseId, null);
            choices = selections(cruiseId);
        }
        if (choices.isEmpty()) return List.of("FARE_PLAN_SELECTION_REQUIRED");
        if (choices.stream().anyMatch(choice ->
                !packages.validPlanChoice(cruiseId, choice.offerId(), choice.priceRevisionId())))
            return List.of("FARE_PLAN_SELECTION_STALE");
        return List.of();
    }

    @Transactional
    public void approve(UUID cruiseId, UUID actor) {
        for (var choice : selections(cruiseId))
            packages.approveForPlan(cruiseId, choice.offerId(), choice.priceRevisionId(), actor);
    }

    @Transactional
    public void recall(UUID cruiseId, UUID actor, String reason) {
        for (var choice : selections(cruiseId))
            packages.recallPriceForPlan(cruiseId, choice.offerId(), choice.priceRevisionId(), actor, reason);
    }

    private long revision(UUID cruiseId) {
        return jdbc.query("""
                SELECT revision FROM "CruisePlanPackageSelection" WHERE cruise_id=?
                """, (rs,n) -> rs.getLong(1), cruiseId).stream().findFirst().orElse(0L);
    }

    private void requireUnified(UUID cruiseId) {
        if (!plans.usesUnifiedPackages(cruiseId))
            throw new BusinessException(HttpStatus.CONFLICT, "FARE_PLAN_SELECTION_UNAVAILABLE",
                    "Chuyến đang sử dụng hồ sơ thương mại theo luồng trước.");
    }
}
