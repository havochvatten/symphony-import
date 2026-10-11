package se.havochvatten.symphonyconfig.setup.model;

import org.apache.commons.cli.ParseException;
import org.apache.commons.imaging.FormatCompliance;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.tiff.TiffDirectory;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffReader;
import org.apache.commons.imaging.formats.tiff.constants.GeoTiffTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;

import java.io.File;
import java.io.IOException;

/**
 * The tags of a baseline raster's first image. Only the image directory is read, never the pixel
 * data: a baseline raster can be gigabytes.
 */
public final class GeoTiff {
    private final String path;
    private final int bandCount;
    private final boolean hasCrs;
    private final boolean hasGeoreferencing;

    private GeoTiff(String path, int bandCount, boolean hasCrs, boolean hasGeoreferencing) {
        this.path = path;
        this.bandCount = bandCount;
        this.hasCrs = hasCrs;
        this.hasGeoreferencing = hasGeoreferencing;
    }

    public static GeoTiff read(String path) throws ParseException {
        try {
            TiffDirectory image = new TiffReader(false)
                .readFirstDirectory(ByteSource.file(new File(path)), false, FormatCompliance.getDefault())
                .directories.get(0);
            TiffField samplesPerPixel = image.findField(TiffTagConstants.TIFF_TAG_SAMPLES_PER_PIXEL);

            return new GeoTiff(path,
                // TIFF 6.0: SamplesPerPixel defaults to 1
                samplesPerPixel == null ? 1 : samplesPerPixel.getIntValue(),
                image.findField(GeoTiffTagConstants.EXIF_TAG_GEO_KEY_DIRECTORY_TAG) != null,
                image.findField(GeoTiffTagConstants.EXIF_TAG_MODEL_TRANSFORMATION_TAG) != null
                    || (image.findField(GeoTiffTagConstants.EXIF_TAG_MODEL_PIXEL_SCALE_TAG) != null
                        && image.findField(GeoTiffTagConstants.EXIF_TAG_MODEL_TIEPOINT_TAG) != null));
        } catch (IOException e) {
            throw new ParseException(String.format("Could not read %s as a TIFF file: %s", path, e.getMessage()));
        }
    }

    public int bandCount() {
        return bandCount;
    }

    /**
     * Symphony reads a raster without a coordinate reference system as WGS84, which places it on the
     * wrong area, and cannot read one without georeferencing at all. Both must be in the raster's own
     * GeoTIFF tags: Symphony stores only the raster's path, so .tfw/.prj files next to it are easily lost.
     */
    public void validateGeoreferencing() throws ParseException {
        if (!hasCrs) {
            throw new ParseException(String.format(
                "%s has no coordinate reference system (GeoTIFF GeoKeyDirectory tag). To embed one, " +
                "e.g. from a .prj file next to it: gdal_translate -a_srs <raster>.prj <raster> <new raster>", path));
        }
        if (!hasGeoreferencing) {
            throw new ParseException(String.format(
                "%s has no georeferencing (GeoTIFF ModelTransformation tag, or ModelPixelScale and " +
                "ModelTiepoint tags). To embed it from a .tfw file next to it: gdal_translate <raster> <new raster>", path));
        }
    }
}
