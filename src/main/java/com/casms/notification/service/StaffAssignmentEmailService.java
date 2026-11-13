package com.casms.notification.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.entity.CruiseStaff;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.casms.notification.service.StaffAssignmentEmailRenderer.*;

@Service
@RequiredArgsConstructor
public class StaffAssignmentEmailService {
    static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;

    @Value("${casms.staff-assignment-email.portal-url:}")
    private String portalUrl;

    public enum Status {
        QUEUED, SENT, MISSING_EMAIL, RECIPIENT_CHANGED, FAILED, CANCELLED
    }

    public record PublishedTemplate(Long version, Template template) {}

    private record Delivery(Status status, String currentEmail) {}

    @Transactional(readOnly = true)
    public PublishedTemplate currentTemplate() {
        var versions = jdbc.query("""
                SELECT version, subject, heading, introduction, image_url, button_label, button_url
                FROM "StaffAssignmentEmailTemplate" ORDER BY version DESC LIMIT 1
                """, (rs, row) -> new PublishedTemplate(rs.getLong("version"), new Template(
                rs.getString("subject"), rs.getString("heading"), rs.getString("introduction"),
                rs.getString("image_url"), rs.getString("button_label"), rs.getString("button_url"))));
        if (!versions.isEmpty()) return versions.getFirst();
        String link = portalUrl == null || portalUrl.isBlank() ? null : portalUrl;
        return new PublishedTemplate(null, validate(new Template(
                "Phân công nhân sự chuyến khởi hành", "Bạn có phân công mới",
                "Vui lòng xem nhiệm vụ và thời gian làm việc dưới đây.",
                null, link == null ? null : "Mở CASMS", link)));
    }

    public Rendered preview(Template template) {
        return render(validate(template), new Assignment(
                "Nguyễn Minh Anh", "CASMS Horizon", "Hành trình ven biển", "CR-001",
                Instant.parse("2026-11-20T01:00:00Z"), Instant.parse("2026-11-21T09:00:00Z"),
                "ONBOARD_STAFF", "Có mặt tại khu vực tập trung trước giờ khởi hành."));
    }

    @Transactional
    public PublishedTemplate publish(Template input, UUID actorId) {
        Template template = validate(input);
        Long version = jdbc.queryForObject("""
                INSERT INTO "StaffAssignmentEmailTemplate"
                    (subject, heading, introduction, image_url, button_label, button_url, published_by)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING version
                """, Long.class, template.subject(), template.heading(), template.introduction(),
                template.imageUrl(), template.buttonLabel(), template.buttonUrl(), actorId);
        return new PublishedTemplate(version, template);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Status enqueue(CruiseStaff staff) {
        var cruise = staff.getCruise();
        var employee = staff.getUser();
        var template = currentTemplate();
        var message = render(template.template(), new Assignment(
                employee.getUsername(), cruise.getShip() == null ? null : cruise.getShip().getShipName(),
                cruise.getCruiseName(), cruise.getCruiseCode(), cruise.getDepartureDatetime(),
                cruise.getReturnDatetime(), staff.getRoleInCruise(), staff.getNotes()));
        String email = recipient(employee.getEmail());
        int inserted = jdbc.update("""
                INSERT INTO "StaffAssignmentEmailOutbox"
                    (assignment_id, template_version, recipient, subject, body_text, body_html)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (assignment_id) DO NOTHING
                """, staff.getCruiseStaffId(), template.version(), email, message.subject(),
                message.text(), message.html());
        if (inserted == 0) return delivery(cruise.getCruiseId(), staff.getCruiseStaffId(), false).status();
        return email == null ? Status.MISSING_EMAIL : Status.QUEUED;
    }

    @Transactional(readOnly = true)
    public Status status(UUID cruiseId, UUID assignmentId) {
        return delivery(cruiseId, assignmentId, false).status();
    }

    @Transactional
    public Status retry(UUID cruiseId, UUID assignmentId, UUID actorId) {
        var current = delivery(cruiseId, assignmentId, true);
        if (current.status() != Status.FAILED && current.status() != Status.MISSING_EMAIL
                && current.status() != Status.RECIPIENT_CHANGED) {
            return current.status();
        }
        String email = recipient(current.currentEmail());
        if (email == null) return Status.MISSING_EMAIL;
        jdbc.update("""
                UPDATE "StaffAssignmentEmailOutbox"
                SET recipient = ?, next_attempt_at = now(), attempts = 0,
                    last_retry_by = ?, last_retry_at = now()
                WHERE assignment_id = ?
                """, email, actorId, assignmentId);
        return Status.QUEUED;
    }

    private Delivery delivery(UUID cruiseId, UUID assignmentId, boolean lock) {
        var rows = jdbc.query("""
                SELECT o.sent_at IS NOT NULL AS sent, o.recipient, o.attempts,
                    cs.status = 'ACTIVE' AND u.status = 'ACTIVE' AS active, u.email
                FROM "StaffAssignmentEmailOutbox" o
                JOIN "CruiseStaff" cs ON cs.cruise_staff_id = o.assignment_id
                JOIN "User" u ON u.user_id = cs.user_id
                WHERE cs.cruise_id = ? AND cs.cruise_staff_id = ?
                    AND NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id = u.user_id AND ur.role = 'ADMIN')
                """ + (lock ? " FOR NO KEY UPDATE OF o, cs, u" : ""), (rs, row) -> {
                    String currentEmail = recipient(rs.getString("email"));
                    String queuedEmail = rs.getString("recipient");
                    Status status;
                    if (rs.getBoolean("sent")) status = Status.SENT;
                    else if (!rs.getBoolean("active")) status = Status.CANCELLED;
                    else if (currentEmail == null || queuedEmail == null) status = Status.MISSING_EMAIL;
                    else if (!queuedEmail.equals(currentEmail)) status = Status.RECIPIENT_CHANGED;
                    else if (rs.getInt("attempts") >= MAX_ATTEMPTS) status = Status.FAILED;
                    else status = Status.QUEUED;
                    return new Delivery(status, currentEmail);
                }, cruiseId, assignmentId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Staff assignment email", assignmentId);
        return rows.getFirst();
    }

    private static String recipient(String value) {
        if (value == null || value.isBlank() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return null;
        }
        try {
            var parsed = new InternetAddress(value.trim(), true);
            parsed.validate();
            return !parsed.isGroup() && value.trim().equals(parsed.getAddress())
                    ? parsed.getAddress() : null;
        } catch (AddressException invalidAddress) {
            return null;
        }
    }
}
