package se.havochvatten.symphony_setup.setup.process;

import org.apache.commons.cli.ParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import se.havochvatten.symphony_setup.setup.config.MetadataImportSettings;
import se.havochvatten.symphony_setup.setup.model.MetaValue;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.tryParseInt;

public class MetadataXlsx extends MetadataBase {

    public MetadataXlsx(MetadataImportSettings settings) throws ParseException {
        super(settings);
        process();
    }

    private static final String alignmentErrorMessage =
        "The input data is incorrectly aligned.\n" +
        "Make sure that the input metadata table is present the top left of the first sheet in the workbook.";

    private String cellValue(Row r, String field) {
        Integer index = fieldsToColumns.get(field);
        if (index == null) return null;

        Cell cell = r.getCell(index);
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> null;
        };
    }

    private SymphonyCategory rowCategory(Row r) {
        return getCategory(cellValue(r, SYMPHONY_CATEGORY));
    }

    @Override
    public boolean validate() {
        try (FileInputStream fs = new FileInputStream(this.settings.inputFilePath)) {
            XSSFRow titleRow    = new XSSFWorkbook(fs).getSheetAt(0).getRow(0);
            if (titleRow.getLastCellNum() == 0) {
                validationErrors.add(alignmentErrorMessage);
                return false;
            }

            int lastTitleColumn = titleRow.getLastCellNum();

            Set<String> allFields = new HashSet<>(lastTitleColumn);
            titleRow.cellIterator()
                .forEachRemaining(c -> allFields.add(c.getStringCellValue().toLowerCase()));

            if(!validateFieldSet(allFields)) {
                return false;
            }

            for (int i = 0; i < lastTitleColumn; ++i) {
                String currentField = titleRow.getCell(i).getStringCellValue();
                if(!fieldsToColumns.containsKey(currentField)) {
                    fieldsToColumns.put(currentField, i);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading input file.", e);
        }

        return true;
    }

    @Override
    public boolean collectBands() {
        try (FileInputStream fs = new FileInputStream(this.settings.inputFilePath)) {
            Sheet sheet = new XSSFWorkbook(fs).getSheetAt(0);

            Iterator<Row> rows = sheet.rowIterator();
            rows.next(); // skip top row (column head)

            while (rows.hasNext()) {
                Row row = rows.next();
                SymphonyCategory c = rowCategory(row);
                String bnStr = cellValue(row, BANDNUMBER);
                Integer bandNumber = tryParseInt(bnStr);

                if (validateBand(bandNumber, bnStr, c, cellValue(row, SYMPHONY_CATEGORY))) {
                    SymphonyBand band = makeBand(bandNumber, cellValue(row, DEFAULT_SELECTED));

                    for (String field : fieldsToColumns.keySet()) {
                        if (field.equals(BANDNUMBER) ||
                            field.equals(DEFAULT_SELECTED) ||
                            field.equals(SYMPHONY_CATEGORY)) continue;
                        band.setMetaValue(settings.language,
                                          new MetaValue(field, cellValue(row, field), settings.language));
                    }

                    bands.get(c).add(band);

                } else {
                    return false;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error reading input file.", e);
        }
        return true;
    }
}
