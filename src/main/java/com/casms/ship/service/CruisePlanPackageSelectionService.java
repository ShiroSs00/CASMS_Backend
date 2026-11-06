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

    public List<String> blockers(UUID cruiseId) {
        var choices = selections(cruiseId);
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
