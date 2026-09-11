package se.havochvatten.symphonyconfig.setup.config;

import org.geotools.coverage.grid.GridCoverage2D;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.io.IOException;

import static se.havochvatten.symphonyconfig.setup.model.Baseline.readTiff;

public abstract class BandsBasedSettingsBase extends TextualSettingsBase {
    public final int ecosystemBandsCount;
    public final int pressureBandsCount;

    protected BandsBasedSettingsBase(
        BaselineVersion baselineVersion,
        String inputFilePath,
        String inputFileOption,
        String typeDescriptor,
        String language,
        String defaultLanguage, int order) throws Exception {

        super(baselineVersion, inputFilePath, inputFileOption, typeDescriptor, language, defaultLanguage, order);
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
