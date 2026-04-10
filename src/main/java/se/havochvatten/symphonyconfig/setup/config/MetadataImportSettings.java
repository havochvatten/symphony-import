package se.havochvatten.symphonyconfig.setup.config;

import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.io.File;

public class MetadataImportSettings extends BandsBasedSettingsBase {

    public String fileName() {
        return new File(inputFilePath).getName();
    }

    public MetadataImportSettings(BaselineVersion baselineVersion, String mdFilePath,
                                  String language, String defaultLanguage, boolean clear, int order) throws Exception {

        super(baselineVersion, mdFilePath, "md", "metadata", language, defaultLanguage, clear, order);

        argMissingDesc      = "Insufficient arguments to carry out the Metadata import procedure";
        validationErrorDesc = "Metadata import - invalid settings";
    }
}
