package se.havochvatten.symphony_setup.setup.config;

import se.havochvatten.symphony_setup.setup.model.BaselineVersion;

import java.io.File;

public class MetadataImportSettings extends BandsBasedSettingsBase {

    public String fileName() {
        return new File(inputFilePath).getName();
    }

    public MetadataImportSettings(BaselineVersion baselineVersion, String mdFilePath,
                                  String language, String defaultLanguage, boolean clear, int order) throws Exception {

        super(baselineVersion, mdFilePath, language, defaultLanguage, clear, order);

        argMissingDesc      = "Insufficient arguments to carry out the Metadata import procedure";
        validationErrorDesc = "Metadata import - invalid settings";
        inputOption         = "md";
    }

    @Override
    public String getTypeDescriptor() {
        return "metadata";
    }
}
