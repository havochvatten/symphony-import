package se.havochvatten.symphony_setup.setup.process;

import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.apache.commons.io.input.BOMInputStream;
import se.havochvatten.symphony_setup.setup.config.MetadataImportSettings;
import se.havochvatten.symphony_setup.setup.model.MetaValue;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.tryParseInt;

public class MetadataCsv extends MetadataBase {

    // Previous import scripts for Symphony metadata have presupposed a nonstandard
    // CSV format (using semicolon as the field delimiter).
    private static final char defaultSeparator = ';';
    private static final String defaultNewLine = "\n";
    private final char separator;
    private final String newLine;

    private final InputStream getBOMSafeStream() throws IOException {
        return BOMInputStream.builder().setInputStream(new FileInputStream(this.settings.inputFilePath)).get();
    }

    private CSVFormat getFormat() {
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(separator)
                .setRecordSeparator(newLine)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
                .build();
    }

    private final Set<String> allFields = new HashSet<>();

    public MetadataCsv(MetadataImportSettings settings, Character separator, String newLine) throws ParseException {
        super(settings);

        this.separator = separator == null ? defaultSeparator : separator;
        this.newLine = newLine == null ? defaultNewLine : newLine;

        process();
    }

    @Override
    public boolean validate() {
        try (InputStream fs = getBOMSafeStream()) {
            Reader fsr = new InputStreamReader(fs, StandardCharsets.UTF_8);

            Iterable<CSVRecord> csvAll = getFormat().parse(fsr);
            csvAll.iterator().next().iterator().forEachRemaining(r -> allFields.add(r.toLowerCase()));

            return validateFieldSet(allFields);
        } catch (IOException e) {
            validationErrors.add("Error reading input file: " + this.settings.inputFilePath);
            return false;
        }
    }

    @Override
    public boolean collectBands() {
        try (InputStream fs = getBOMSafeStream()) {
            Reader fsr = new InputStreamReader(fs, StandardCharsets.UTF_8);

            Iterable<CSVRecord> csvBands = getFormat().withHeader().parse(fsr);
            for (CSVRecord csvBand : csvBands) {
                String cStr = csvBand.get(SYMPHONY_CATEGORY);
                SymphonyCategory c = getCategory(cStr);
                String bnStr = csvBand.get(BANDNUMBER);
                Integer bandNumber = tryParseInt(bnStr);

                if (validateBand(bandNumber, bnStr, c, cStr)) {
                    SymphonyBand band = makeBand(bandNumber, csvBand.get(DEFAULT_SELECTED));

                    for (String field : allFields) {
                        if (field.equals(BANDNUMBER) ||
                            field.equals(DEFAULT_SELECTED) ||
                            field.equals(SYMPHONY_CATEGORY)) continue;

                        band.setMetaValue(settings.language,
                             new MetaValue(field, csvBand.get(field), settings.language));
                    }

                    bands.get(c).add(band);
                }
            }

        } catch (IOException e) {
            validationErrors.add("Error reading input file: " + this.settings.inputFilePath);
            return false;
        }
        return true;
    }
}
