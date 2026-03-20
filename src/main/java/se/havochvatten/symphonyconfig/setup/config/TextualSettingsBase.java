package se.havochvatten.symphonyconfig.setup.config;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.io.File;

import static se.havochvatten.symphonyconfig.setup.SymphonySetup.*;
import static se.havochvatten.symphonyconfig.setup.SymphonySetup.Util.ordinal;

public abstract class TextualSettingsBase extends SettingsBase {

    public SupportedTabularFileFormat format;
    public final String language;
    public final int order;

    public TextualSettingsBase(BaselineVersion baselineVersion, String inputFilePath, String inputFileOption,
                               String typeDescriptor, String language, String defaultLanguage, boolean clear, int order) {
        super(baselineVersion, inputFilePath, inputFileOption, typeDescriptor, clear);

        this.order = order;

        if (language != null) {
            this.language = language.toLowerCase();
        } else {
            this.language = defaultLanguage;

            if(order > 0) {
                validationMessages.add(String.format("Language parameter missing for the %s provided %s file.\n" +
                    "Falling back to '%s'", ordinal(order + 1), getTypeDescriptor(), defaultLanguage));
            } else {
                validationMessages.add(String.format("No language parameter specified.\n" +
                    "Falling back to the baseline default: '%s'", defaultLanguage));
            }
        }
    }

    @Override
    public boolean validate() {
        boolean valid =  super.validate();

        valid &= validateLanguage();
        valid &= validateInputFile();

        return valid;
    }

    protected boolean validateLanguage() {
        if (language != null) {
            if (!ISO_LANG.contains(language)) {
                validationErrors.add(
                    String.format("A provided language parameter: '%s', is not a valid ISO 639-1 language code.", language));
                return false;
            }
            if (!SYM_LANG.contains(language)) {
                validationMessages.add(
                    String.format("A provided language parameter: '%s', is not supported by the default UI", language));
            }
            return true;
        }
        return false;
    }

    private boolean validateInputFile() {
        if (inputFilePath != null) {
            File mdFile = new File(inputFilePath);
            if (mdFile.isFile()) {
                String ext = FilenameUtils.getExtension(inputFilePath);
                if (!SUPPORTED_EXT.contains(ext.toUpperCase())) {
                    validationErrors.add(
                        String.format("A %s file (%s) seems to be provided in an unsupported format.\n" +
                                "Allowed file types are (%s)",
                            getTypeDescriptor(),
                            inputFilePath,
                            StringUtils.join(SupportedTabularFileFormat.values(), ", ").toLowerCase())
                    );
                    return false;
                }
                this.format = SupportedTabularFileFormat.valueOf(ext.toUpperCase());
                return true;
            } else {
                validationErrors.add(
                    String.format("The provided %s file path (%s) is invalid.",
                                  getTypeDescriptor(), inputFilePath));
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings (value="unchecked")
    public static <T extends TextualSettingsBase> T create(Class<T> type, BaselineVersion baselineVersion,
                                                           String inputFilePath, String language,
                                                           String defaultLanguage, boolean clear, int order) throws Exception {
        return switch(type.getSimpleName()) {
            case "MetadataImportSettings" -> (T) new MetadataImportSettings(baselineVersion, inputFilePath,
                                                  language, defaultLanguage, clear, order);
            case "MatrixImportSettings" ->   (T) new MatrixImportSettings(baselineVersion, inputFilePath,
                                                  language, defaultLanguage, clear, order);
            default -> throw new IllegalStateException("Unexpected type: " + type.getName());
        };
    }
}
