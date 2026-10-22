package com.casms.common.service;

import com.casms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessCodeGeneratorTest {

    @Test
    void formatsSequenceValuesAndKeepsPrefixesIndependent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT nextval('casms_ship_code_seq')", Long.class)).thenReturn(1L);
        when(jdbc.queryForObject("SELECT nextval('casms_cruise_code_seq')", Long.class)).thenReturn(42L);

        BusinessCodeGenerator generator = new BusinessCodeGenerator(jdbc);

        assertEquals("SH-0001", generator.next("SH"));
        assertEquals("CR-0042", generator.next("CR"));
    }

    @Test
    void reportsSequenceExhaustionClearly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SQLException exhausted = new SQLException("maximum value reached", "2200H");
        when(jdbc.queryForObject("SELECT nextval('casms_ship_code_seq')", Long.class))
                .thenThrow(new org.springframework.jdbc.UncategorizedSQLException("nextval", "sequence", exhausted));

        BusinessException error = assertThrows(BusinessException.class,
                () -> new BusinessCodeGenerator(jdbc).next("SH"));
        assertEquals("BUSINESS_CODE_EXHAUSTED", error.getCode());
    }
}
