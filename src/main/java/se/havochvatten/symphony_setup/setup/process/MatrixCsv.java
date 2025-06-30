package se.havochvatten.symphony_setup.setup.process;

import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import se.havochvatten.symphony_setup.setup.config.CSVSettings;
import se.havochvatten.symphony_setup.setup.config.MatrixImportSettings;
import se.havochvatten.symphony_setup.setup.model.Baseline;
import se.havochvatten.symphony_setup.setup.model.MatrixCategoryTitlesMap;
import se.havochvatten.symphony_setup.setup.model.Sensitivity;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static se.havochvatten.symphony_setup.setup.config.CSVSettings.getBOMSafeStream;

public class MatrixCsv extends MatrixBase {

    CSVSettings csvSettings;

    public MatrixCsv(MatrixImportSettings settings, Baseline baseline, CSVSettings csvSettings) throws ParseException {
        super(settings, baseline);
        this.csvSettings = csvSettings;
        process();
    }

    List<String> getEcoKeys(List<CSVRecord> records) {
        return records.get(0).stream().toList().subList(1, records.get(0).size());
    }

    List<String> getPressureKeys(List<CSVRecord> records) {
        return records.stream().map(s -> s.get(0)).toList().subList(1, records.size());
    }

    @Override
    public boolean validate() {
        try (InputStream fs = getBOMSafeStream(this.settings.inputFilePath);
             Reader fsr = new InputStreamReader(fs, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(fsr, csvSettings.getFormat());
        ) {
            List<CSVRecord> mxRowsList = parser.getRecords();
            Set<String> ecoKeys      = new HashSet<>(getEcoKeys(mxRowsList));
            Set<String> pressureKeys = new HashSet<>(getPressureKeys(mxRowsList));

            if (!validateMatrixBands(SymphonyCategory.ECOSYSTEM, ecoKeys)) {
                return false;
            }

            if(!validateMatrixBands(SymphonyCategory.PRESSURE, pressureKeys)) {
                return false;
            };
        } catch (IOException e) {
            validationErrors.add("Error reading input matrix file: " + this.settings.inputFilePath);
            return false;
        }

        return true;
    }

    @Override
    public boolean collect() {
        try (InputStream fs = getBOMSafeStream(this.settings.inputFilePath);
             Reader fsr = new InputStreamReader(fs, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(fsr, csvSettings.getFormat());
        ) {
            List<CSVRecord> mxRowsList = parser.getRecords();
            List<String> ecoKeys      = getEcoKeys(mxRowsList);
            List<String> pressureKeys = getPressureKeys(mxRowsList);

            this.sensitivities = new ArrayList<>(ecoKeys.size() * pressureKeys.size());

            MatrixCategoryTitlesMap componentTitleIdMapping =
                new MatrixCategoryTitlesMap(ecoKeys, pressureKeys,
                    currentBaseline.bandsByCategory(SymphonyCategory.ECOSYSTEM),
                    currentBaseline.bandsByCategory(SymphonyCategory.PRESSURE),
                    settings.language);

            int r = 0;

            for (CSVRecord record : mxRowsList.subList(1, mxRowsList.size())) {
                for (int i = 0; i < ecoKeys.size(); ++i) {
                    sensitivities.add(
                        new Sensitivity(
                            componentTitleIdMapping.getTitleIdsMap(SymphonyCategory.ECOSYSTEM).get(ecoKeys.get(i)),
                            componentTitleIdMapping.getTitleIdsMap(SymphonyCategory.PRESSURE).get(pressureKeys.get(r)),
                            Double.parseDouble(record.get(i + 1)))
                    );
                }
                ++r;
            }
        } catch (IOException e) {
            validationErrors.add("Error reading input matrix file: \"" + this.settings.getMatrixName() + "\"");
            return false;
        }

        return true;
    }
}
