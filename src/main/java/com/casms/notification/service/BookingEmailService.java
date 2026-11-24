package com.casms.notification.service;

import com.casms.booking.entity.Booking;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingEmailService {
    public static final int MIN_ACTIVATION_PASSWORD_LENGTH = 8;
    public static final int MAX_ACTIVATION_PASSWORD_LENGTH = 24;
    public static final int MAX_ACTIVATION_TOKEN_LENGTH = 128;

    private static final String TEMP_PASS_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TEMP_PASS_LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_PASS_DIGIT = "23456789";
    private static final String TEMP_PASS_ALL = TEMP_PASS_UPPER + TEMP_PASS_LOWER + TEMP_PASS_DIGIT;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VN_ZONE);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    @Value("${casms.booking-email.portal-url:http://localhost:5173}")
    private String portalUrl;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockContact(String email) {
        // Serialize this provisioning flow; the database unique index also protects other writers.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", email);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void markProvisioned(User user) {
        jdbc.update("INSERT INTO \"BookingAccountSetup\" (user_id) VALUES (?) ON CONFLICT DO NOTHING", user.getUserId());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueueBookingCreated(Booking booking) {
        User user = booking.getBookedByUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
        String event = "BOOKING_CREATED:" + booking.getBookingId();
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM \"BookingEmailOutbox\" WHERE event_key = ?)", Boolean.class, event))) return;
        String tempPassword = provisionTempPasswordIfNew(user);
        String detailUrl = portalUrl + "/my-bookings/" + booking.getBookingId();
        String subject = "[OceVie] Đã tạo đặt chỗ " + booking.getBookingReference() + " — " + booking.getCruise().getCruiseName();
        String html = buildHtmlEmail(
                "Đặt chỗ của bạn đã được khởi tạo",
                "Cảm ơn bạn đã đặt hành trình cùng OceVie. Vui lòng hoàn tất thanh toán đúng hạn để giữ cabin.",
                booking,
                null,
                user.getUsername(),
                tempPassword,
                detailUrl);
        jdbc.update("INSERT INTO \"BookingEmailOutbox\" (event_key, booking_id, recipient, subject, body) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                event, booking.getBookingId(), user.getEmail(), subject, html);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueueConfirmation(Booking booking, BigDecimal paid) {
        User user = booking.getBookedByUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "BOOKING_EMAIL_REQUIRED", "A booking contact email is required");
        }
        String event = "BOOKING_CONFIRMED:" + booking.getBookingId();
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM \"BookingEmailOutbox\" WHERE event_key = ?)", Boolean.class, event))) return;
        String tempPassword = provisionTempPasswordIfNew(user);
        String detailUrl = portalUrl + "/my-bookings/" + booking.getBookingId();
        String subject = "[OceVie] Xác nhận đặt chỗ " + booking.getBookingReference() + " — " + booking.getCruise().getCruiseName();
        String html = buildHtmlEmail(
                "Đặt chỗ đã được xác nhận",
                "OceVie đã xác nhận đặt chỗ của bạn. Bạn có thể theo dõi lịch trình, lịch thanh toán và làm thủ tục check-in trực tuyến ngay trên cổng khách hàng.",
                booking,
                paid,
                user.getUsername(),
                tempPassword,
                detailUrl);
        jdbc.update("INSERT INTO \"BookingEmailOutbox\" (event_key, booking_id, recipient, subject, body) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                event, booking.getBookingId(), user.getEmail(), subject, html);
    }

    private String provisionTempPasswordIfNew(User user) {
        // Only accounts newly provisioned by booking get a random temporary password; existing accounts remain untouched.
        var pending = jdbc.queryForList(
                "SELECT user_id FROM \"BookingAccountSetup\" WHERE user_id = ? AND activated_at IS NULL AND token_hash IS NULL FOR UPDATE",
                UUID.class, user.getUserId());
        if (pending.isEmpty()) return null;
        String tempPassword = generateTempPassword();
        jdbc.update("UPDATE \"User\" SET password_hash = ?, updated_at = now() WHERE user_id = ? AND status = 'ACTIVE'",
                passwordEncoder.encode(tempPassword), user.getUserId());
        jdbc.update("UPDATE \"BookingAccountSetup\" SET activated_at = now(), token_hash = NULL WHERE user_id = ?",
                user.getUserId());
        return tempPassword;
    }

    private String generateTempPassword() {
        SecureRandom rng = new SecureRandom();
        char[] chars = new char[10];
        chars[0] = TEMP_PASS_UPPER.charAt(rng.nextInt(TEMP_PASS_UPPER.length()));
        chars[1] = TEMP_PASS_LOWER.charAt(rng.nextInt(TEMP_PASS_LOWER.length()));
        chars[2] = TEMP_PASS_DIGIT.charAt(rng.nextInt(TEMP_PASS_DIGIT.length()));
        for (int i = 3; i < chars.length; i++) {
            chars[i] = TEMP_PASS_ALL.charAt(rng.nextInt(TEMP_PASS_ALL.length()));
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) return "0 " + (currency != null ? currency : "VND");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("VND".equalsIgnoreCase(currency) ? "#,##0" : "#,##0.##", symbols);
        return df.format(amount) + " " + (currency != null ? currency : "VND");
    }

    private String formatInstant(Instant instant) {
        return instant != null ? VN_TIME.format(instant) + " (giờ VN)" : "—";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String buildHtmlEmail(
            String heading,
            String intro,
            Booking booking,
            BigDecimal paid,
            String username,
            String tempPassword,
            String detailUrl) {
        String currency = booking.getCurrency() != null ? booking.getCurrency() : "VND";
        BigDecimal total = booking.getAgreedFareAmount() != null ? booking.getAgreedFareAmount() : BigDecimal.ZERO;
        BigDecimal paidAmt = paid != null ? paid : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(paidAmt).max(BigDecimal.ZERO);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"vi\"><body style=\"margin:0;padding:24px;background:#F8F6EC;font-family:'Segoe UI',Arial,sans-serif;color:#0B2A4A;\">");
        sb.append("<div style=\"max-width:600px;margin:0 auto;background:#FFFFFF;border:1px solid #D5E0E4;border-radius:12px;overflow:hidden;\">");
        sb.append("<div style=\"background:#061A2B;padding:20px 24px;border-bottom:3px solid #D4AF37;color:#F8F6EC;\">");
        sb.append("<div style=\"font-size:18px;font-weight:700;letter-spacing:0.06em;\">OCEVIE CRUISES</div>");
        sb.append("<div style=\"font-size:13px;color:#A9C7CF;margin-top:4px;\">").append(esc(heading)).append("</div></div>");
        sb.append("<div style=\"padding:24px;\">");
        sb.append("<p style=\"margin:0 0 16px;font-size:14px;line-height:1.6;\">").append(esc(intro)).append("</p>");
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px;margin-bottom:16px;\">");
        sb.append("<tr><td style=\"padding:8px 0;color:#506777;border-bottom:1px solid #EDF3F1;\">Mã đặt chỗ</td><td style=\"padding:8px 0;font-weight:700;text-align:right;border-bottom:1px solid #EDF3F1;\">").append(esc(booking.getBookingReference())).append("</td></tr>");
        sb.append("<tr><td style=\"padding:8px 0;color:#506777;border-bottom:1px solid #EDF3F1;\">Hành trình</td><td style=\"padding:8px 0;font-weight:600;text-align:right;border-bottom:1px solid #EDF3F1;\">").append(esc(booking.getCruise().getCruiseName())).append("</td></tr>");
        sb.append("<tr><td style=\"padding:8px 0;color:#506777;border-bottom:1px solid #EDF3F1;\">Khởi hành</td><td style=\"padding:8px 0;text-align:right;border-bottom:1px solid #EDF3F1;\">").append(esc(formatInstant(booking.getCruise().getDepartureDatetime()))).append("</td></tr>");
        sb.append("<tr><td style=\"padding:8px 0;color:#506777;border-bottom:1px solid #EDF3F1;\">Trở về</td><td style=\"padding:8px 0;text-align:right;border-bottom:1px solid #EDF3F1;\">").append(esc(formatInstant(booking.getCruise().getReturnDatetime()))).append("</td></tr>");
        sb.append("<tr><td style=\"padding:8px 0;color:#506777;border-bottom:1px solid #EDF3F1;\">Tổng giá vé</td><td style=\"padding:8px 0;font-weight:700;text-align:right;border-bottom:1px solid #EDF3F1;\">").append(esc(formatMoney(total, currency))).append("</td></tr>");
        if (paid != null) {
            sb.append("<tr><td style=\"padding:8px 0;color:#506777;border-bottom:1px solid #EDF3F1;\">Đã thanh toán</td><td style=\"padding:8px 0;color:#167347;font-weight:700;text-align:right;border-bottom:1px solid #EDF3F1;\">").append(esc(formatMoney(paidAmt, currency))).append("</td></tr>");
            sb.append("<tr><td style=\"padding:8px 0;color:#506777;\">Còn lại</td><td style=\"padding:8px 0;font-weight:600;text-align:right;\">").append(esc(formatMoney(remaining, currency))).append("</td></tr>");
        }
        sb.append("</table>");

        if (tempPassword != null) {
            sb.append("<div style=\"background:#F3EBDD;border:1px solid #DBC9A7;border-radius:8px;padding:16px;margin:16px 0;\">");
            sb.append("<div style=\"font-weight:700;font-size:14px;margin-bottom:8px;\">Thông tin tài khoản khách hàng mới</div>");
            sb.append("<p style=\"margin:0 0 8px;font-size:13px;color:#506777;\">Hệ thống đã tự động tạo tài khoản để bạn quản lý đặt chỗ, thanh toán VietQR và làm thủ tục check-in trực tuyến:</p>");
            sb.append("<div style=\"font-size:14px;line-height:1.7;\"><strong>Tên đăng nhập:</strong> <code>").append(esc(username)).append("</code><br/>");
            sb.append("<strong>Mật khẩu tạm:</strong> <code>").append(esc(tempPassword)).append("</code></div>");
            sb.append("<p style=\"margin:8px 0 0;font-size:12px;color:#506777;\">Vui lòng bảo mật thông tin này và đổi mật khẩu sau khi đăng nhập.</p></div>");
        } else if (username != null) {
            sb.append("<p style=\"font-size:13px;color:#506777;margin:12px 0;\">Tài khoản quản lý đặt chỗ: <strong>").append(esc(username)).append("</strong> (giữ nguyên mật khẩu hiện tại của bạn).</p>");
        }

        sb.append("<div style=\"margin-top:20px;text-align:center;\">");
        sb.append("<a href=\"").append(esc(detailUrl)).append("\" style=\"display:inline-block;background:#0B2A4A;color:#FFFFFF;text-decoration:none;padding:12px 24px;border-radius:6px;font-weight:600;font-size:14px;\">Xem chi tiết đặt chỗ</a>");
        sb.append("</div></div></div></body></html>");
        return sb.toString();
    }

    @Transactional
    public void activate(String token, String password) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Password must fit 72 UTF-8 bytes");
        }
        if (password == null || password.isBlank()
                || password.length() < MIN_ACTIVATION_PASSWORD_LENGTH
                || password.length() > MAX_ACTIVATION_PASSWORD_LENGTH) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_LENGTH",
                    "Password must be 8–24 characters and cannot be blank");
        }
        if (token == null || token.isBlank() || token.length() > MAX_ACTIVATION_TOKEN_LENGTH) {
            throw invalidSetupToken();
        }
        var users = jdbc.queryForList("SELECT user_id FROM \"BookingAccountSetup\" WHERE token_hash = ? AND activated_at IS NULL AND expires_at > now() FOR UPDATE",
                UUID.class, hash(token));
        if (users.isEmpty()) throw invalidSetupToken();
        UUID userId = users.get(0);
        int updated = jdbc.update("UPDATE \"User\" SET password_hash = ?, updated_at = now() WHERE user_id = ? AND status = 'ACTIVE'",
                passwordEncoder.encode(password), userId);
        if (updated != 1) throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Account is disabled");
        jdbc.update("UPDATE \"BookingAccountSetup\" SET activated_at = now(), token_hash = NULL WHERE user_id = ?", userId);
        // Remove the bearer setup link from retained outbox bodies after successful activation.
        jdbc.update("UPDATE \"BookingEmailOutbox\" SET body = 'Account setup completed' WHERE booking_id IN (SELECT booking_id FROM \"Booking\" WHERE booked_by_user_id = ?) AND body LIKE '%activate-booking-account#token=%'", userId);
    }

    private BusinessException invalidSetupToken() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "ACCOUNT_SETUP_INVALID", "Setup token is invalid or expired");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
