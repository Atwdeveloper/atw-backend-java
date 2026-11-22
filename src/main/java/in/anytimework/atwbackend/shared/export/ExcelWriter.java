package in.anytimework.atwbackend.shared.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Writes one sheet of tabular data to .xlsx bytes, for admin-list export endpoints across modules
 * (worker, company, ...) that previously hand-rolled their own CSV writer.
 *
 * <p>Plain {@link XSSFWorkbook}, not {@code SXSSFWorkbook}: the whole workbook is built in memory
 * rather than streamed row-by-row. That is fine at today's admin-export scale - the same deliberate
 * limitation the earlier CSV writer called out - and should move to a streaming writer instead of a
 * larger in-memory buffer if exports grow to a size where that matters. No column-width or
 * {@code autoSizeColumn} logic either: a deliberate scope limit, not an oversight.</p>
 *
 * <p>Pure POJO-in, POJO-out: no {@code ResponseEntity} and no content-type/header concerns. Each
 * calling controller sets its own {@code Content-Disposition} header and the
 * {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet} content type.</p>
 *
 * <p>Cell values are {@link String} or {@code null} (-> blank cell) only - callers are expected to
 * format dates, booleans, enums, etc. into strings themselves before calling this, the same division
 * of responsibility the CSV export already uses.</p>
 */
public final class ExcelWriter {

    private ExcelWriter() {
    }

    /**
     * Builds a single-sheet workbook: {@code headers} becomes a bold header row, then one row per
     * entry in {@code rows}, each inner list read in the same column order as {@code headers}.
     */
    public static byte[] write(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                List<Object> rowData = rows.get(i);
                for (int col = 0; col < rowData.size(); col++) {
                    setCellValue(row.createCell(col), rowData.get(col));
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write xlsx workbook", e);
        }
    }

    /** {@code null} is left as a blank cell; anything else is written as its string form. */
    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }
}
