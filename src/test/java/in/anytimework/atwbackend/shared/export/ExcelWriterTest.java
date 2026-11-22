package in.anytimework.atwbackend.shared.export;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelWriterTest {

    @Test
    void write_producesAReadableWorkbook_withBoldHeaderRow_andBlankCellsForNulls() throws IOException {
        List<String> headers = List.of("Worker Code", "Name", "City");
        List<List<Object>> rows = List.of(
                Arrays.asList("W-001", "Asha Rao", "Pune"),
                Arrays.asList("W-002", null, "Mumbai"));

        byte[] xlsx = ExcelWriter.write("Workers", headers, rows);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Workers");

            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Worker Code");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Name");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("City");

            CellStyle headerStyle = headerRow.getCell(0).getCellStyle();
            Font headerFont = workbook.getFontAt(headerStyle.getFontIndex());
            assertThat(headerFont.getBold()).isTrue();

            Row firstDataRow = sheet.getRow(1);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("W-001");
            assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("Asha Rao");
            assertThat(firstDataRow.getCell(2).getStringCellValue()).isEqualTo("Pune");

            Row secondDataRow = sheet.getRow(2);
            assertThat(secondDataRow.getCell(0).getStringCellValue()).isEqualTo("W-002");
            assertThat(secondDataRow.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(secondDataRow.getCell(2).getStringCellValue()).isEqualTo("Mumbai");
        }
    }
}
