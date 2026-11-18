package com.casms.payment.service;

import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.payment.enums.RefundStatus;
import com.casms.payment.repository.RefundRepository;
import com.casms.security.principal.CasmsPrincipal;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundReportingService {
    private final RefundRepository refunds;
    private final AuditRepository audit;

    public record Totals(String currency, RefundStatus status, long count,
            BigDecimal requestedAmount, BigDecimal approvedAmount) {}

    @Transactional(readOnly = true)
    public List<Totals> summary(CasmsPrincipal actor, Instant from, Instant until) {
        requireFinance(actor);
        return totals(from, until);
    }

    @Transactional
    public String csv(CasmsPrincipal actor, Instant from, Instant until) {
        requireFinance(actor);
        if (!actor.hasAuthority("REPORT_GENERATE")) throw new AccessDeniedException("Không có quyền xuất báo cáo.");
        StringBuilder result = new StringBuilder("currency,status,count,requested_amount,approved_amount\r\n");
        for (var row : totals(from, until)) {
            String currency = row.currency().matches("[A-Z]{3}") ? row.currency() : "UNKNOWN";
            result.append(currency).append(',').append(row.status()).append(',').append(row.count())
                    .append(',').append(row.requestedAmount().toPlainString()).append(',')
                    .append(row.approvedAmount().toPlainString()).append("\r\n");
        }
        var entry = new AuditLog();
        entry.setActorUserId(actor.getUserId());
        entry.setAction("REFUND_REPORT_EXPORT");
        entry.setEntityType("Refund");
        entry.setResult("SUCCESS");
        entry.setDetail("Requested at from " + from + " until " + until);
        audit.saveAndFlush(entry);
        return result.toString();
    }

    private List<Totals> totals(Instant from, Instant until) {
        if (from == null || until == null || !from.isBefore(until)
                || Duration.between(from, until).compareTo(Duration.ofDays(366)) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFUND_REPORT_INVALID_RANGE",
                    "Chọn khoảng ngày hợp lệ trong tối đa 366 ngày.");
        }
        return refunds.totalsByCurrencyAndStatus(from, until).stream()
                .map(row -> new Totals(row.getCurrency().strip(), row.getStatus(), row.getCount(),
                        row.getRequestedAmount(), row.getApprovedAmount()))
                .toList();
    }

    private static void requireFinance(CasmsPrincipal actor) {
        if (actor == null || actor.getUserId() == null || !actor.hasRole(Role.FINANCE_OFFICER)
                || !actor.hasAuthority("PAYMENT_REFUND")) {
            throw new AccessDeniedException("Báo cáo hoàn tiền dành cho bộ phận tài chính.");
        }
    }
}
