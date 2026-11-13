package com.casms.notification.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "casms.staff-assignment-email.enabled", havingValue = "true")
public class StaffAssignmentEmailDispatcher {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${casms.staff-assignment-email.from:${casms.booking-email.from:}}")
    private String from;

    @PostConstruct
    void validateConfiguration() {
        if (from == null || from.isBlank() || !from.contains("@") || mailSender.getIfAvailable() == null) {
            throw new IllegalStateException("Staff assignment email requires SMTP and a sender address");
        }
    }

    // The outbox is visible only after assignment commit. SMTP and SQL do not share a transaction.
    @Scheduled(fixedDelayString = "${casms.staff-assignment-email.poll-ms:30000}")
    @Transactional
    public void dispatch() {
        var sender = mailSender.getObject();
        var rows = jdbc.queryForList("""
                SELECT o.outbox_id, o.recipient, o.subject, o.body_text, o.body_html
                FROM "StaffAssignmentEmailOutbox" o
                JOIN "CruiseStaff" cs ON cs.cruise_staff_id = o.assignment_id
                JOIN "User" u ON u.user_id = cs.user_id
                WHERE o.sent_at IS NULL AND o.recipient IS NOT NULL AND o.attempts < 5
                    AND o.next_attempt_at <= now() AND cs.status = 'ACTIVE' AND u.status = 'ACTIVE'
                    AND o.recipient = btrim(u.email)
                ORDER BY o.next_attempt_at, o.outbox_id LIMIT 10
                FOR NO KEY UPDATE OF o, cs, u SKIP LOCKED
                """);
        for (var row : rows) {
            UUID id = (UUID) row.get("outbox_id");
            try {
                var message = sender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo((String) row.get("recipient"));
                helper.setSubject((String) row.get("subject"));
                helper.setText((String) row.get("body_text"), (String) row.get("body_html"));
                sender.send(message);
            } catch (MessagingException | MailException failure) {
                jdbc.update("""
                        UPDATE "StaffAssignmentEmailOutbox" SET attempts = attempts + 1,
                            next_attempt_at = now() + interval '5 minutes' WHERE outbox_id = ?
                        """, id);
                continue;
            }
            jdbc.update("""
                    UPDATE "StaffAssignmentEmailOutbox" SET attempts = attempts + 1, sent_at = now()
                    WHERE outbox_id = ?
                    """, id);
        }
    }
}
