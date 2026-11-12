package com.casms.identity.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.identity.service.EmployeeAccountService.CreateInput;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;

public final class EmployeeWorkbook {
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final List<String> COLUMNS = List.of(
            "employeeCode", "fullName", "email", "phone", "gender", "dateOfBirth",
            "address", "department", "position", "role");
    private static final int MAX_ROWS = 500;
    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 16L * 1024 * 1024;

    private EmployeeWorkbook() {}

    public record InputRow(int number, CreateInput input, String error) {}

    public static byte[] template(List<Role> allowed) {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            Sheet employees = book.createSheet("Employees");
            write(employees.createRow(0), COLUMNS);
            Sheet roles = book.createSheet("Roles");
            write(roles.createRow(0), List.of("role"));
            for (int i = 0; i < allowed.size(); i++) write(roles.createRow(i + 1), List.of(allowed.get(i).name()));
            book.write(bytes);
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not write employee workbook", failure);
        }
    }

    public static byte[] results(List<EmployeeImportService.RowResult> rows) {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Results");
            write(sheet.createRow(0), List.of("row", "employeeCode", "status", "error"));
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                write(sheet.createRow(i + 1), List.of(Integer.toString(row.rowNumber()),
                        orEmpty(row.employeeCode()), row.result(), orEmpty(row.error())));
            }
            book.write(bytes);
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not write employee workbook results", failure);
        }
    }

    public static List<InputRow> read(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) {
            throw invalid("File XLSX phải có dữ liệu và không vượt quá 2 MB.");
        }
        try {
            requireBoundedZip(bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet sheet = workbook.getSheet("Employees");
                if (sheet == null || sheet.getRow(0) == null) throw invalid("Thiếu sheet Employees.");
                var format = new DataFormatter(Locale.ROOT);
                for (int i = 0; i < COLUMNS.size(); i++) {
                    if (!COLUMNS.get(i).equals(format.formatCellValue(sheet.getRow(0).getCell(i)).trim())) {
                        throw invalid("Tiêu đề cột không khớp mẫu XLSX.");
                    }
                }
                if (sheet.getRow(0).getLastCellNum() > COLUMNS.size() || sheet.getLastRowNum() > MAX_ROWS) {
                    throw invalid("File vượt số cột của mẫu hoặc giới hạn 500 dòng.");
                }
                List<InputRow> rows = new ArrayList<>();
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    var parsed = readRow(row, format);
                    if (parsed != null) rows.add(parsed);
                }
                if (rows.isEmpty()) throw invalid("File chưa có dòng nhân viên.");
                return List.copyOf(rows);
            }
        } catch (BusinessException expected) {
            throw expected;
        } catch (IOException | RuntimeException malformed) {
            throw invalid("Không đọc được file XLSX.");
        }
    }

    private static InputRow readRow(Row row, DataFormatter format) {
        int number = row.getRowNum() + 1;
        List<String> values = new ArrayList<>(COLUMNS.size());
        for (int i = 0; i < COLUMNS.size(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)) {
                return new InputRow(number, null, "FORMULA_OR_CELL_ERROR");
            }
            String value = format.formatCellValue(cell).trim();
            values.add(value.isEmpty() ? null : value);
        }
        if (values.stream().allMatch(java.util.Objects::isNull)) return null;
        try {
            Cell birthCell = row.getCell(5);
            LocalDate birth = values.get(5) == null ? null
                    : birthCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(birthCell)
                        ? birthCell.getLocalDateTimeCellValue().toLocalDate()
                    : LocalDate.parse(values.get(5));
            Role role = values.get(9) == null ? null : Role.valueOf(values.get(9).toUpperCase(Locale.ROOT));
            return new InputRow(number, new CreateInput(
                    values.get(0), values.get(1), values.get(2), values.get(3), values.get(4),
                    birth, values.get(6), values.get(7), values.get(8), role), null);
        } catch (IllegalArgumentException | java.time.DateTimeException invalidValue) {
            return new InputRow(number, null, "INVALID_ROLE_OR_DATE");
        }
    }

    private static void requireBoundedZip(byte[] bytes) throws IOException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long expanded = 0;
            int entries = 0;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 100) throw invalid("File XLSX có quá nhiều thành phần.");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > MAX_EXPANDED_BYTES) throw invalid("File XLSX vượt giới hạn sau giải nén.");
                }
            }
        }
    }

    private static void write(Row row, List<String> values) {
        for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "EMPLOYEE_XLSX_INVALID", message);
    }
}
