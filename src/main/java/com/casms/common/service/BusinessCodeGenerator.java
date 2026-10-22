package com.casms.common.service;

import com.casms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Generates user-facing business codes from database sequences. */
@Service
@RequiredArgsConstructor
public class BusinessCodeGenerator {

    private static final Map<String, String> SEQUENCES = Map.ofEntries(
            Map.entry("SH", "casms_ship_code_seq"),
            Map.entry("CR", "casms_cruise_code_seq"),
            Map.entry("BK", "casms_booking_code_seq"),
            Map.entry("OI", "casms_operational_issue_code_seq"),
            Map.entry("RG", "casms_operational_regulation_code_seq"),
            Map.entry("AC", "casms_activity_code_seq"),
            Map.entry("SV", "casms_service_code_seq"),
            Map.entry("PR", "casms_product_code_seq"),
            Map.entry("PK", "casms_package_code_seq"),
            Map.entry("EX", "casms_excursion_code_seq"),
            Map.entry("TG", "casms_tour_group_code_seq")
    );

    private final JdbcTemplate jdbcTemplate;

    public String next(String prefix) {
        String sequence = SEQUENCES.get(prefix);
        if (sequence == null) {
            throw new IllegalArgumentException("Unsupported business code prefix");
        }
        try {
            long value = jdbcTemplate.queryForObject("SELECT nextval('" + sequence + "')", Long.class);
            return prefix + "-" + String.format("%04d", value);
        } catch (org.springframework.dao.DataAccessException ex) {
            Throwable cause = ex;
            while (cause != null) {
                if (cause instanceof java.sql.SQLException sqlException
                        && "2200H".equals(sqlException.getSQLState())) {
                    throw new BusinessException("BUSINESS_CODE_EXHAUSTED",
                            "No business codes remain for prefix " + prefix + " (maximum is " + prefix + "-9999)");
                }
                cause = cause.getCause();
            }
            throw ex;
        }
    }
}
