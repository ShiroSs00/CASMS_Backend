package com.casms.notification.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "casms.booking-email.enabled", havingValue = "true")
public class BookingEmailDispatcher {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSender;
    @Value("${casms.booking-email.from}") private String from;

    @jakarta.annotation.PostConstruct
    void validateConfiguration() {
        if (from == null || from.isBlank() || !from.contains("@")) {
            throw new IllegalStateException("Booking email dispatch requires a valid sender address");
        }
        if (mailSender.getIfAvailable() == null) {
            throw new IllegalStateException("Booking email dispatch requires SMTP configuration");
        }
    }

    /** At-least-once delivery: SKIP LOCKED coordinates workers; SMTP cannot promise exactly once. */
    @Scheduled(fixedDelayString = "${casms.booking-email.poll-ms:30000}")
    @Transactional
    public void dispatch() {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) return;
        var rows = jdbc.queryForList("SELECT event_key, recipient, subject, body FROM \"BookingEmailOutbox\" WHERE sent_at IS NULL AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT 10 FOR UPDATE SKIP LOCKED");
        for (var row : rows) {
            String key = (String) row.get("event_key");
            try {
                String body = (String) row.get("body");
                boolean isHtml = body != null && body.trim().startsWith("<");
                MimeMessage mime = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
                helper.setFrom(from);
                helper.setTo((String) row.get("recipient"));
                helper.setSubject((String) row.get("subject"));
                helper.setText(body != null ? body : "", isHtml);
                sender.send(mime);
                jdbc.update("UPDATE \"BookingEmailOutbox\" SET sent_at = now(), attempts = attempts + 1 WHERE event_key = ?", key);
            } catch (Exception ex) {
                jdbc.update("UPDATE \"BookingEmailOutbox\" SET attempts = attempts + 1, next_attempt_at = now() + interval '5 minutes' WHERE event_key = ?", key);
            }
        }
    }
}
