package com.casms.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.casms.common.exception.BusinessException;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

class BusinessCodeGeneratorTest {

    @Test
    void formatsSequenceValuesAndKeepsPrefixesIndependent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT nextval(?)", Long.class, "casms_ship_code_seq"))
                .thenReturn(1L);
        when(jdbc.queryForObject("SELECT nextval(?)", Long.class, "casms_cruise_code_seq"))
                .thenReturn(42L);

        BusinessCodeGenerator generator = new BusinessCodeGenerator(jdbc);

        assertEquals("SH-0001", generator.next("SH"));
        assertEquals("CR-0042", generator.next("CR"));
    }

    @Test
    void reportsSequenceExhaustionClearly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SQLException exhausted = new SQLException("maximum value reached", "2200H");
        when(jdbc.queryForObject("SELECT nextval(?)", Long.class, "casms_ship_code_seq"))
                .thenThrow(
                        new org.springframework.jdbc.UncategorizedSQLException(
                                "nextval", "sequence", exhausted));

        BusinessCodeGenerator generator = new BusinessCodeGenerator(jdbc);
        BusinessException error =
                assertThrows(
                        BusinessException.class, () -> generator.next("SH"));
        assertEquals("BUSINESS_CODE_EXHAUSTED", error.getCode());
    }
}
