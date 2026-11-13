package com.casms.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class StaffAssignmentEmailDispatcherTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private ObjectProvider<JavaMailSender> senderProvider;
    @Mock private JavaMailSender sender;

    private final UUID outboxId = UUID.randomUUID();
    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    private StaffAssignmentEmailDispatcher dispatcher;

    @BeforeEach
    void readyOutbox() {
        dispatcher = new StaffAssignmentEmailDispatcher(jdbc, senderProvider);
        ReflectionTestUtils.setField(dispatcher, "from", "crew@example.org");
        when(senderProvider.getObject()).thenReturn(sender);
        when(jdbc.queryForList(contains("SKIP LOCKED"))).thenReturn(List.of(Map.of(
                "outbox_id", outboxId, "recipient", "employee@example.org", "subject", "Phân công",
                "body_text", "Lịch công tác", "body_html", "<strong>Lịch công tác</strong>")));
        when(sender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void sendsPlainTextAndHtmlBeforeMarkingOutboxSent() throws Exception {
        dispatcher.dispatch();
        message.saveChanges();

        assertEquals("Phân công", message.getSubject());
        assertEquals("Lịch công tác", part(message, "text/plain"));
        assertEquals("<strong>Lịch công tác</strong>", part(message, "text/html"));
        var ordered = inOrder(sender, jdbc);
        ordered.verify(sender).send(message);
        ordered.verify(jdbc).update(contains("sent_at = now()"), eq(outboxId));
    }

    @Test
    void transportFailureSchedulesRetryWithoutMarkingSent() {
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(message);

        dispatcher.dispatch();

        verify(jdbc).update(contains("next_attempt_at = now()"), eq(outboxId));
        verify(jdbc, never()).update(contains("sent_at = now()"), eq(outboxId));
    }

    private static String part(Part parent, String contentType) throws Exception {
        if (parent.isMimeType(contentType)) return parent.getContent().toString();
        if (!(parent.getContent() instanceof Multipart multipart)) return null;
        for (int i = 0; i < multipart.getCount(); i++) {
            String result = part(multipart.getBodyPart(i), contentType);
            if (result != null) return result;
        }
        return null;
    }
}
