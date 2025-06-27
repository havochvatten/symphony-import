package se.havochvatten.symphony_setup.setup.config;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;
import se.havochvatten.symphony_setup.setup.model.ProcedureBase;

import java.io.File;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.*;
import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.ordinal;

public abstract class SettingsBase extends ProcedureBase {

    public final BaselineVersion baselineVersion;

    protected String typeDescriptor;
    protected String inputOption;
    public String inputFilePath;
    public SupportedTabularFileFormat format;
    public final String language;
    public final boolean clear;
    public final int order;

    public SettingsBase(BaselineVersion baselineVersion, String inputFilePath,
                        String language, String defaultLanguage, boolean clear, int order) {
        this.baselineVersion = baselineVersion;
        this.clear = clear;
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

        if (inputFilePath != null) {
            this.inputFilePath = inputFilePath;
        } else {
            missingArgs.add(inputOption);
        }
    }

    public String getTypeDescriptor() {
        return typeDescriptor;
    }

    public boolean validate() {
        boolean valid =  super.validate();

        valid &= validateLanguage(language);
        valid &= validateInputFile();

        return valid;
    }

    protected boolean validateLanguage(String lang) {
        if (language != null) {
            if (!ISO_LANG.contains(lang)) {
                validationErrors.add(
                    String.format("A provided language parameter: '%s', is not a valid ISO 639-1 language code.", language));
                return false;
            }
            if (!SYM_LANG.contains(lang)) {
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
    public static <T extends SettingsBase> T create(Class<T> type, BaselineVersion baselineVersion,
                                                    String inputFilePath, String language,
                                                    String defaultLanguage, boolean clear, int order) throws Exception {
        return switch(type.getSimpleName()) {
            case "MetadataImportSettings" -> (T) new MetadataImportSettings(baselineVersion, inputFilePath,
                                                  language, defaultLanguage, clear, order);
            case "MatrixImportSettings" ->   (T) new MatrixImportSettings(baselineVersion, inputFilePath,
                                                  language, defaultLanguage, clear, order);
            default -> throw new IllegalStateException("Unexpected value: " + type.getName());
        };
    }
}
