package se.havochvatten.symphony_setup.setup.process;

import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVRecord;
import se.havochvatten.symphony_setup.setup.config.CSVSettings;
import se.havochvatten.symphony_setup.setup.config.MetadataImportSettings;
import se.havochvatten.symphony_setup.setup.model.MetaValue;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.tryParseInt;
import static se.havochvatten.symphony_setup.setup.config.CSVSettings.getBOMSafeStream;

public class MetadataCsv extends MetadataBase {

    CSVSettings csvSettings;

    private final Set<String> allFields = new HashSet<>();

    public MetadataCsv(MetadataImportSettings settings, CSVSettings csvSettings) throws ParseException {
        super(settings);
        this.csvSettings = csvSettings;
        process();
    }

    @Override
    public boolean validate() {
        try (InputStream fs = getBOMSafeStream(this.settings.inputFilePath)) {
            Reader fsr = new InputStreamReader(fs, StandardCharsets.UTF_8);

            Iterable<CSVRecord> csvAll = csvSettings.getFormat().parse(fsr);
            csvAll.iterator().next().iterator().forEachRemaining(r -> allFields.add(r.toLowerCase()));

            return validateFieldSet(allFields);
        } catch (IOException e) {
            validationErrors.add("Error reading input metadata file: " + this.settings.inputFilePath);
            return false;
        }
    }

    @Override
    public boolean collect() {
        try (InputStream fs = getBOMSafeStream(this.settings.inputFilePath)) {
            Reader fsr = new InputStreamReader(fs, StandardCharsets.UTF_8);

            Iterable<CSVRecord> csvBands = csvSettings.getFormat().withHeader().parse(fsr);
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
            validationErrors.add("Error reading input metadata file: " + this.settings.inputFilePath);
            return false;
        }
        return true;
    }
}
