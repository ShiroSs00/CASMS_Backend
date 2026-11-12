package com.casms.identity.service;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.identity.enums.Role;
import com.casms.identity.service.EmployeeImportService.RowResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class EmployeeWorkbookTest {
    @Test
    void readsRealWorkbookAndRejectsFormulaRowsWithoutEvaluatingThem() throws Exception {
        byte[] template = EmployeeWorkbook.template(List.of(Role.ONBOARD_STAFF));
        byte[] filled;
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template));
                var bytes = new ByteArrayOutputStream()) {
            assertEquals("ONBOARD_STAFF", book.getSheet("Roles").getRow(1).getCell(0).getStringCellValue());
            var sheet = book.getSheet("Employees");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("EMP-01");
            row.createCell(1).setCellValue("Nguyễn Minh Anh");
            row.createCell(2).setCellValue("employee@example.org");
            row.createCell(5).setCellValue("1995-09-07");
            row.createCell(9).setCellValue("ONBOARD_STAFF");
            sheet.createRow(2).createCell(0).setCellFormula("1+1");
            book.write(bytes);
            filled = bytes.toByteArray();
        }

        var rows = EmployeeWorkbook.read(filled);

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(1995, 9, 7), rows.getFirst().input().dateOfBirth());
        assertEquals("Nguyễn Minh Anh", rows.getFirst().input().fullName());
        assertEquals(Role.ONBOARD_STAFF, rows.getFirst().input().role());
        assertEquals("FORMULA_OR_CELL_ERROR", rows.getLast().error());
    }

    @Test
    void resultContainsOnlySafeResultColumns() throws Exception {
        byte[] bytes = EmployeeWorkbook.results(List.of(new RowResult(2, "EMP-01", "CREATED", null, UUID.randomUUID())));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheet("Results");
            assertEquals(4, sheet.getRow(0).getLastCellNum());
            assertEquals("status", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("CREATED", sheet.getRow(1).getCell(2).getStringCellValue());
        }
    }
}
