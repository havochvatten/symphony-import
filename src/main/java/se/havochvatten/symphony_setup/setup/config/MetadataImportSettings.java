package se.havochvatten.symphony_setup.setup.config;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;
import se.havochvatten.symphony_setup.setup.model.ProcedureBase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.ISO_LANG;
import static se.havochvatten.symphony_setup.setup.SymphonySetup.SYM_LANG;
import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.ordinal;
import static se.havochvatten.symphony_setup.setup.model.Baseline.readTiff;

public class MetadataImportSettings extends ProcedureBase {
    private static final Set<String> SUPPORTED_EXT =
        Set.of(Arrays.stream(SupportedMetadataFormat.values())
            .map(Enum::name).toArray(String[]::new));

    public final BaselineVersion baselineVersion;

    public String inputFilePath;
    public SupportedMetadataFormat format;
    public final String language;
    public final boolean clear;
    public final int order;

    public final int ecosystemBandsCount;
    public final int pressureBandsCount;

    public String fileName() {
        return new File(inputFilePath).getName();
    }

    public MetadataImportSettings(BaselineVersion blv, String mdFilePath, String language, String defaultLanguage, boolean _clear, int _order)
        throws Exception {

        argMissingDesc      = "Required arguments missing from the Metadata import invocation";
        validationErrorDesc = "Metadata import - invalid settings";
        baselineVersion = blv;
        clear = _clear;
        order = _order;

        GridCoverage2D coverage;

        try {
            coverage = readTiff(baselineVersion.getEcoFilename());
            ecosystemBandsCount = coverage.getSampleDimensions().length;
            coverage = readTiff(baselineVersion.getPressureFilename());
            pressureBandsCount = coverage.getSampleDimensions().length;
        } catch (IOException e) {
            throw new Exception("Fatal error: failed to load baseline GeoTIFF");
        }

        if (language != null) {
            this.language = language.toLowerCase();
        } else {
            this.language = defaultLanguage;

            if(order > 0) {
                validationMessages.add(String.format("Language parameter missing for the %s provided metadata file.\n" +
                                                "Falling back to '%s'", ordinal(order + 1), defaultLanguage));
            } else {
                validationMessages.add(String.format("No language parameter specified.\n" +
                                                "Falling back to the baseline default: '%s'", defaultLanguage));
            }
        }

        if (mdFilePath != null) {
            inputFilePath = mdFilePath;
        } else {
            missingArgs.add("md");
        }
    }

    private boolean validateLanguage() {
        if (language != null) {
            if (!ISO_LANG.contains(language)) {
                validationErrors.add(
                    String.format("The provided language parameter '%s' is not a valid ISO 639-1 language code.", language));
                return false;
            }
            if (!SYM_LANG.contains(language)) {
                validationMessages.add(
                    String.format("The provided language parameter '%s' is not supported by the default UI", language));
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
                        String.format("A metadata input file (%s) seems to be provided in an unsupported format.\n" +
                                      "Allowed metadata input file types are (%s)",
                                      inputFilePath,
                                      StringUtils.join(SupportedMetadataFormat.values(), ", ").toLowerCase())
                    );
                    return false;
                }
                this.format = SupportedMetadataFormat.valueOf(ext.toUpperCase());
                return true;
            } else {
                validationErrors.add("The provided metadata input file path is invalid.");
                return false;
            }
        }
        return false;
    }

    public boolean validate() {
        boolean valid =  super.validate();

        valid &= validateLanguage();
        valid &= validateInputFile();

        return valid;
    }
}
