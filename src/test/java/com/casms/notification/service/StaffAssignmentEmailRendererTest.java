package com.casms.notification.service;

import static com.casms.notification.service.StaffAssignmentEmailRenderer.*;
import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class StaffAssignmentEmailRendererTest {
    @Test
    void rendersVietnameseScheduleWithSafeImageAndButton() {
        var template = validate(new Template("Phân công", "Lịch công tác", "Vui lòng có mặt đúng giờ.",
                "https://example.org/banner.png", "Xem phân công", "https://example.org/staff"));
        var message = render(template, new Assignment("Minh Anh", "Horizon", "Hành trình ven biển",
                "CR-001", Instant.parse("2026-11-20T01:00:00Z"), null, "FINANCE_OFFICER", "Ca lễ tân"));

        assertTrue(message.text().contains("08:00 20/11/2026 (giờ Việt Nam)"));
        assertTrue(message.text().contains("Tài chính/Lễ tân"));
        assertTrue(message.text().contains("Xem phân công: https://example.org/staff"));
        assertTrue(message.html().contains("<img src=\"https://example.org/banner.png\""));
        assertTrue(message.html().contains("href=\"https://example.org/staff\""));
        assertTrue(message.html().contains("Ca lễ tân"));
    }

    @Test
    void escapesTemplateTextAndAssignmentNotes() {
        var template = validate(new Template("Phân công", "<script>title</script>", "Dòng một\nDòng hai",
                null, null, null));
        var message = render(template, new Assignment("<b>Minh</b>", "Horizon", "Hành trình", "CR-001",
                null, null, "ONBOARD_STAFF", "<img src=x onerror=alert(1)>"));

        assertFalse(message.html().contains("<script>"));
        assertFalse(message.html().contains("<img src=x"));
        assertTrue(message.html().contains("&lt;img src=x"));
        assertTrue(message.html().contains("Dòng một<br>Dòng hai"));
    }

    @Test
    void rejectsUnsafeUrlsAndMultilineSubjects() {
        assertThrows(BusinessException.class, () -> validate(new Template(
                "Phân công", "Tiêu đề", "Nội dung", "javascript:alert(1)", null, null)));
        assertThrows(BusinessException.class, () -> validate(new Template(
                "Phân công", "Tiêu đề", "Nội dung", null, "Mở", "https://user@example.org/")));
        assertThrows(BusinessException.class, () -> validate(new Template(
                "Phân công\nBcc: other@example.org", "Tiêu đề", "Nội dung", null, null, null)));
    }
}
