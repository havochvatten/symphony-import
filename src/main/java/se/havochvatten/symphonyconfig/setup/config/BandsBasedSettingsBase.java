package se.havochvatten.symphonyconfig.setup.config;

import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;
import se.havochvatten.symphonyconfig.setup.model.GeoTiff;

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

        ecosystemBandsCount = GeoTiff.read(baselineVersion.getEcoFilePath()).bandCount();
        pressureBandsCount = GeoTiff.read(baselineVersion.getPressureFilePath()).bandCount();
    }
}
