package se.havochvatten.symphony_setup.setup.config;

import org.geotools.coverage.grid.GridCoverage2D;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;

import java.io.IOException;

import static se.havochvatten.symphony_setup.setup.model.Baseline.readTiff;

public abstract class BandsBasedSettingsBase extends TextualSettingsBase {
    public final int ecosystemBandsCount;
    public final int pressureBandsCount;

    protected BandsBasedSettingsBase(
        BaselineVersion baselineVersion,
        String inputFilePath,
        String inputFileOption,
        String typeDescriptor,
        String language,
        String defaultLanguage, boolean clear, int order) throws Exception {

        super(baselineVersion, inputFilePath, inputFileOption, typeDescriptor, language, defaultLanguage, clear, order);
        GridCoverage2D coverage;

        try {
            coverage = readTiff(baselineVersion.getEcoFilePath());
            ecosystemBandsCount = coverage.getSampleDimensions().length;
            coverage = readTiff(baselineVersion.getPressureFilePath());
            pressureBandsCount = coverage.getSampleDimensions().length;
        } catch (IOException e) {
            throw new Exception("Fatal error: failed to load baseline GeoTIFF");
        }
    }
}
