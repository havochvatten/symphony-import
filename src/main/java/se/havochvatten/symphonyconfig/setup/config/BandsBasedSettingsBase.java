package se.havochvatten.symphonyconfig.setup.config;

import org.apache.commons.cli.ParseException;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

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

        ecosystemBandsCount = countBands(baselineVersion.getEcoFilePath());
        pressureBandsCount = countBands(baselineVersion.getPressureFilePath());
    }

    public static int countBands(String path) throws ParseException {
            try {
                File tiffFile = Path.of(path).toFile();
                ImageMetadata metadata = Imaging.getMetadata(tiffFile);

                if (metadata instanceof TiffImageMetadata tiffMetadata) {
                    TiffField samplesPerPixelField = tiffMetadata.findField(TiffTagConstants.TIFF_TAG_SAMPLES_PER_PIXEL);

                    if (samplesPerPixelField != null) {
                        return samplesPerPixelField.getIntValue();
                    }
                }
                throw new ParseException("Invalid GeoTIFF metadata");
            } catch (IOException e) {
                // Logically unreachable
                throw new ParseException("Fatal error: failed to read GeoTIFF file");
            }
    }
}
