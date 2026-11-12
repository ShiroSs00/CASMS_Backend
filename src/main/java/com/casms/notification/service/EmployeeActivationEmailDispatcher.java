package com.casms.notification.service;

import com.casms.identity.service.EmployeeActivationToken;

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
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "casms.employee-activation-email.enabled", havingValue = "true")
public class EmployeeActivationEmailDispatcher {
    private final JdbcTemplate jdbc;
    private final EmployeeActivationToken tokens;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${casms.employee-activation-email.from:${casms.staff-assignment-email.from:${casms.booking-email.from:}}}")
    private String from;

    @Value("${casms.employee-activation-email.portal-url:}")
    private String portalUrl;

    @PostConstruct
    void validateConfiguration() {
        URI portal = URI.create(portalUrl);
        if (!"https".equalsIgnoreCase(portal.getScheme()) || portal.getHost() == null
                || portal.getFragment() != null || portal.getUserInfo() != null
                || !tokens.isConfigured() || from.isBlank() || mailSender.getIfAvailable() == null) {
            throw new IllegalStateException("Employee activation email requires an HTTPS portal, token key and SMTP");
        }
    }

    @Scheduled(fixedDelayString = "${casms.employee-activation-email.poll-ms:30000}")
    @Transactional
    public void dispatch() {
        var sender = mailSender.getObject();
        var rows = jdbc.queryForList("""
                SELECT o.activation_id, e.full_name, u.email
                FROM "EmployeeActivationOutbox" o JOIN "Employee" e USING (employee_id)
                JOIN "User" u USING (user_id)
                WHERE o.sent_at IS NULL AND o.consumed_at IS NULL AND o.attempts < 5
                    AND o.next_attempt_at <= now() AND o.expires_at > now()
                    AND e.status = 'PENDING_ACTIVATION' AND u.status = 'DISABLED'
                ORDER BY o.next_attempt_at, o.activation_id LIMIT 10
                FOR NO KEY UPDATE OF o, e, u SKIP LOCKED
                """);
        for (var row : rows) {
            UUID id = (UUID) row.get("activation_id");
            String name = (String) row.get("full_name");
            String link = portalUrl + "#token=" + tokens.create(id);
            try {
                var message = sender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo((String) row.get("email"));
                helper.setSubject("Kích hoạt tài khoản nhân viên CASMS");
                helper.setText(
                        "Xin chào " + name + ",\n\nMở liên kết để đặt mật khẩu tài khoản CASMS:\n" + link,
                        """
                        <!doctype html><html lang="vi"><meta charset="utf-8">
                        <body style="background:#eef2f7;font:16px Arial,sans-serif;color:#0f172a">
                        <div style="margin:20px auto;max-width:560px;padding:24px;background:#fff;border-radius:8px">
                        <h1 style="font-size:24px;color:#12344d">Kích hoạt tài khoản CASMS</h1>
                        <p>Xin chào %s,</p><p>Chọn nút bên dưới để đặt mật khẩu cho tài khoản nhân viên.</p>
                        <p><a href="%s" style="display:inline-block;background:#0f4c81;color:white;padding:12px 16px;border-radius:6px;text-decoration:none">Đặt mật khẩu</a></p>
                        <p>Liên kết chỉ dùng được một lần.</p></div></body></html>
                        """.formatted(escape(name), escape(link)));
                sender.send(message);
            } catch (MessagingException | MailException failure) {
                jdbc.update("""
                        UPDATE "EmployeeActivationOutbox" SET attempts = attempts + 1,
                            next_attempt_at = now() + interval '5 minutes' WHERE activation_id = ?
                        """, id);
                continue;
            }
            jdbc.update("""
                    UPDATE "EmployeeActivationOutbox" SET attempts = attempts + 1, sent_at = now()
                    WHERE activation_id = ?
                    """, id);
        }
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
