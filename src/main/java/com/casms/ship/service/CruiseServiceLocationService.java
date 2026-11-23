package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Cruise;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CruiseServiceLocationService {
    private final CruiseRepository cruises;
    private final DeckServiceAssignmentService deckServices;
    private final JdbcTemplate jdbc;

    public record Option(UUID id, String name) {}
    public record Request(UUID serviceId, String serviceName, String deckName,
                          String status, String note, String reviewNote, long revision) {}
    public record View(boolean canPropose, boolean canReview, List<Option> services,
                       List<Option> decks, List<Request> requests, String operatingBudgetVnd, boolean shipAssigned,
                       UUID shipId, String deckBlocker) {}
    public record InboxItem(UUID cruiseId, String name, String code, long pending) {}

    @Transactional(readOnly = true)
    public List<InboxItem> inbox(UUID actorId) {
        return jdbc.query("""
                SELECT c.cruise_id,c.cruise_name,c.cruise_code,count(*)
                FROM "CruiseServiceLocationRequest" r
                JOIN "Cruise" c USING(cruise_id) JOIN "Deck" d ON d.deck_id=r.deck_id AND d.ship_id=c.ship_id
                WHERE r.status='PENDING' AND c.status='DRAFT' AND EXISTS (
                    SELECT 1 FROM "CruiseStaff" a WHERE a.cruise_id=c.cruise_id AND a.user_id=?
                    AND a.role_in_cruise='SHIP_MANAGER' AND a.status='ACTIVE')
                GROUP BY c.cruise_id,c.cruise_name,c.cruise_code,c.departure_datetime
                ORDER BY c.departure_datetime,c.cruise_id
                """, (rs, i) -> new InboxItem(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getLong(4)), actorId);
    }

    private boolean asm(CasmsPrincipal actor) {
        return actor.hasRole(Role.ACTIVITY_SERVICE_MANAGER) && actor.hasAuthority("TOUR_PACKAGE_MANAGE");
    }

    private boolean shipManager(UUID cruiseId, CasmsPrincipal actor) {
        return actor.hasRole(Role.SHIP_MANAGER) && actor.hasAuthority("CRUISE_PREPARATION_READ")
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "CruiseStaff" WHERE cruise_id=? AND user_id=?
                      AND role_in_cruise='SHIP_MANAGER' AND status='ACTIVE')
                """, Boolean.class, cruiseId, actor.getUserId()));
    }

    @Transactional(readOnly = true)
    public View view(UUID cruiseId, CasmsPrincipal actor) {
        return view(cruises.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId)), actor);
    }

    private View view(Cruise cruise, CasmsPrincipal actor) {
        UUID id = cruise.getCruiseId();
        boolean proposer = asm(actor);
        boolean reviewer = shipManager(id, actor);
        if (!proposer && !reviewer) throw forbidden();
        boolean editable = cruise.getStatus() == CruiseStatus.DRAFT && cruise.getShip() != null;
        var requests = jdbc.query("""
                SELECT r.service_id,s.service_name,coalesce(d.deck_name,'Boong ' || d.deck_number),
                       CASE WHEN d.ship_id IS DISTINCT FROM c.ship_id THEN 'RETURNED' ELSE r.status END,
                       r.note,CASE WHEN d.ship_id IS DISTINCT FROM c.ship_id
                           THEN 'Tour đã đổi tàu. Chọn boong trên tàu mới.' ELSE r.review_note END,r.revision
                FROM "CruiseServiceLocationRequest" r JOIN "Service" s USING(service_id) JOIN "Deck" d USING(deck_id)
                JOIN "Cruise" c ON c.cruise_id=r.cruise_id
                WHERE r.cruise_id=? ORDER BY r.requested_at DESC, r.service_id
                """, (rs, i) -> new Request(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7)), id);
        var services = proposer && editable ? jdbc.query("""
                SELECT service_id, service_name FROM "Service" WHERE status='ACTIVE' ORDER BY service_name,service_id
                """, (rs, i) -> new Option(rs.getObject(1, UUID.class), rs.getString(2))) : List.<Option>of();
        var decks = cruise.getShip() != null ? jdbc.query("""
                SELECT deck_id,coalesce(deck_name,'Boong ' || deck_number) FROM "Deck"
                WHERE ship_id=? ORDER BY deck_number
                """, (rs, i) -> new Option(rs.getObject(1, UUID.class), rs.getString(2)),
                cruise.getShip().getShipId()) : List.<Option>of();
        var budget = cruise.getOperatingBudgetVnd();
        boolean shipAssigned = reviewer || proposer && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "CruiseStaff" WHERE cruise_id=?
                      AND role_in_cruise='SHIP_MANAGER' AND status='ACTIVE')
                """, Boolean.class, id));
        UUID shipId = cruise.getShip() == null ? null : cruise.getShip().getShipId();
        String deckBlocker = shipId == null ? "SHIP_REQUIRED" : decks.isEmpty() ? "SHIP_DECK_REQUIRED" : null;
        return new View(proposer && editable && !decks.isEmpty(), reviewer && editable, services, decks, requests,
                budget == null ? null : budget.toPlainString(), shipAssigned, shipId, deckBlocker);
    }

    @Transactional
    public View propose(UUID cruiseId, UUID serviceId, UUID deckId, String note, CasmsPrincipal actor) {
        if (!asm(actor)) throw forbidden();
        Cruise cruise = lockDraft(cruiseId);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "Deck" d CROSS JOIN "Service" s
                     WHERE d.deck_id=? AND d.ship_id=? AND s.service_id=? AND s.status='ACTIVE')
                """, Boolean.class, deckId, cruise.getShip().getShipId(), serviceId))) {
            throw conflict("SERVICE_LOCATION_INVALID", "Chọn dịch vụ đang hoạt động và boong phục vụ trên tàu của tour.");
        }
        int changed = jdbc.update("""
                INSERT INTO "CruiseServiceLocationRequest" AS r
                    (cruise_id,service_id,deck_id,requested_by,note,status)
                VALUES (?,?,?,?,?,'PENDING')
                ON CONFLICT (cruise_id, service_id) DO UPDATE SET
                    deck_id=excluded.deck_id, requested_by=excluded.requested_by, requested_at=now(),
                    note=excluded.note, status='PENDING', reviewed_by=NULL, review_note=NULL, revision=r.revision+1
                WHERE r.status='RETURNED' OR NOT EXISTS (
                    SELECT 1 FROM "Deck" d WHERE d.deck_id=r.deck_id AND d.ship_id=?)
                """, cruiseId, serviceId, deckId, actor.getUserId(), note, cruise.getShip().getShipId());
        if (changed == 0) throw conflict("SERVICE_LOCATION_ALREADY_SENT", "Dịch vụ này đã có đề nghị bố trí.");
        return view(cruise, actor);
    }

    @Transactional
    public View review(UUID cruiseId, UUID serviceId, long revision, boolean confirm, String note,
                       CasmsPrincipal actor) {
        if (!shipManager(cruiseId, actor)) throw forbidden();
        Cruise cruise = lockDraft(cruiseId);
        if (!shipManager(cruiseId, actor)) throw forbidden();
        if (!confirm && (note == null || note.isBlank())) {
            throw conflict("SERVICE_LOCATION_REASON_REQUIRED", "Nhập lý do để ASM biết nội dung cần bổ sung.");
        }
        if (confirm) {
            List<UUID> deckIds = jdbc.query("""
                    SELECT r.deck_id FROM "CruiseServiceLocationRequest" r
                    JOIN "Deck" d USING(deck_id) JOIN "Service" s USING(service_id)
                    WHERE r.cruise_id=? AND r.service_id=? AND d.ship_id=? AND s.status='ACTIVE'
                    """, (rs, i) -> rs.getObject(1, UUID.class), cruiseId, serviceId, cruise.getShip().getShipId());
            if (deckIds.isEmpty()) {
                throw conflict("SERVICE_LOCATION_NOT_AVAILABLE",
                        "Boong đề xuất không thuộc tàu hiện tại hoặc dịch vụ đã ngừng hoạt động.");
            }
            // Idempotently bind service to the confirmed deck on this ship (same pattern as StaffingWorkflowService)
            deckServices.assign(deckIds.get(0), serviceId, cruise.getShip().getShipId());
        }
        int changed = jdbc.update("""
                UPDATE "CruiseServiceLocationRequest"
                SET status=?, reviewed_by=?, review_note=?, revision=revision+1
                WHERE cruise_id=? AND service_id=? AND status='PENDING' AND revision=? AND requested_by<>?
                """, confirm ? "CONFIRMED" : "RETURNED", actor.getUserId(), note,
                cruiseId, serviceId, revision, actor.getUserId());
        if (changed == 0) throw conflict("SERVICE_LOCATION_REVIEW_CHANGED",
                "Đề nghị đã thay đổi hoặc do chính bạn gửi. Tải lại để kiểm tra.");
        return view(cruise, actor);
    }

    private Cruise lockDraft(UUID id) {
        Cruise cruise = cruises.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", id));
        if (cruise.getStatus() != CruiseStatus.DRAFT || cruise.getShip() == null) {
            throw conflict("SERVICE_LOCATION_TOUR_NOT_EDITABLE", "Chỉ bố trí dịch vụ cho tour đang chuẩn bị và đã có tàu.");
        }
        return cruise;
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "SERVICE_LOCATION_FORBIDDEN",
                "Bạn không có quyền xử lý đề nghị bố trí dịch vụ của tour này.");
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
