package com.casms.notification.service;

import com.casms.common.exception.BusinessException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class StaffAssignmentEmailRenderer {
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.ROOT)
                    .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private StaffAssignmentEmailRenderer() {}

    public record Template(
            @NotBlank @Size(max = 180) String subject,
            @NotBlank @Size(max = 180) String heading,
            @NotBlank @Size(max = 4000) String introduction,
            @Size(max = 2048) String imageUrl,
            @Size(max = 80) String buttonLabel,
            @Size(max = 2048) String buttonUrl) {}

    public record Assignment(
            String username, String shipName, String cruiseName, String cruiseCode,
            Instant departure, Instant arrival, String role, String notes) {}

    public record Rendered(String subject, String text, String html) {}

    public static Template validate(Template input) {
        String subject = input.subject().trim();
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw invalid("Tiêu đề email phải nằm trên một dòng.");
        }
        String imageUrl = optional(input.imageUrl());
        String label = optional(input.buttonLabel());
        String link = optional(input.buttonUrl());
        if ((label == null) != (link == null)) {
            throw invalid("Nút bấm cần cả nhãn và đường dẫn.");
        }
        requireHttps(imageUrl);
        requireHttps(link);
        return new Template(subject, input.heading().trim(), input.introduction().trim(), imageUrl, label, link);
    }

    public static Rendered render(Template template, Assignment data) {
        String[] labels = {"Tàu", "Chuyến", "Khởi hành", "Kết thúc", "Nhiệm vụ", "Ghi chú"};
        String[] values = {
            value(data.shipName()), value(data.cruiseCode()) + " · " + value(data.cruiseName()),
            time(data.departure()), time(data.arrival()), roleLabel(data.role()), value(data.notes())
        };
        String greeting = "Xin chào " + value(data.username()) + ",";
        StringBuilder text = new StringBuilder(template.heading()).append("\n\n")
                .append(greeting).append("\n").append(template.introduction()).append("\n\n");
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            text.append(labels[i]).append(": ").append(values[i]).append('\n');
            rows.append("<tr><th scope=\"row\" align=\"left\" style=\"padding:10px;color:#334155;border-bottom:1px solid #e2e8f0\">")
                    .append(labels[i]).append("</th><td style=\"padding:10px;border-bottom:1px solid #e2e8f0\">")
                    .append(paragraph(values[i])).append("</td></tr>");
        }
        String image = template.imageUrl() == null ? "" :
                "<img src=\"" + escape(template.imageUrl()) + "\" alt=\"\" width=\"552\""
                        + " style=\"display:block;width:100%;max-width:552px;height:auto;border:0;border-radius:8px;margin-bottom:20px\">";
        String button = "";
        if (template.buttonUrl() != null) {
            text.append('\n').append(template.buttonLabel()).append(": ").append(template.buttonUrl());
            button = "<p style=\"margin:24px 0 0\"><a style=\"display:inline-block;padding:12px 18px;"
                    + "background:#0f4c81;color:#fff;text-decoration:none;border-radius:6px;font-weight:600\" href=\""
                    + escape(template.buttonUrl()) + "\">" + escape(template.buttonLabel()) + "</a></p>";
        }
        String html = """
                <!doctype html><html lang="vi"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;background:#eef2f7;color:#0f172a;font-family:Arial,sans-serif">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr><td align="center" style="padding:16px">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#fff;border-radius:10px">
                <tr><td style="background:#12344d;color:#fff;padding:20px 24px;font-size:20px;font-weight:bold">CASMS</td></tr>
                <tr><td style="padding:24px;font-size:16px;line-height:1.55">
                %s<h1 style="margin:0 0 16px;font-size:24px">%s</h1>
                <p>%s<br>%s</p>
                <table width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;background:#f8fafc">%s</table>
                %s</td></tr></table></td></tr></table></body></html>
                """.formatted(image, escape(template.heading()), escape(greeting),
                paragraph(template.introduction()), rows, button);
        return new Rendered(template.subject(), text.toString(), html);
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "SHIP_MANAGER" -> "Quản lý trên tàu";
            case "ONBOARD_STAFF" -> "Nhân viên";
            case "FINANCE_OFFICER" -> "Tài chính/Lễ tân";
            default -> value(role);
        };
    }

    private static String time(Instant value) {
        return value == null ? "Chưa xác định" : DISPLAY_TIME.format(value) + " (giờ Việt Nam)";
    }

    private static String paragraph(String value) {
        return escape(value).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "Chưa xác định" : value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    private static void requireHttps(String value) {
        if (value == null) return;
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null) return;
        } catch (IllegalArgumentException ignored) {
            // Return the same validation error for malformed and unsupported URLs.
        }
        throw invalid("Ảnh và nút bấm phải dùng URL HTTPS hợp lệ.");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "STAFF_EMAIL_TEMPLATE_INVALID", message);
    }
}
